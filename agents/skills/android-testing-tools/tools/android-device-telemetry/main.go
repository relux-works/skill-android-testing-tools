package main

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

const schemaVersion = 1

type options struct {
	mode     string
	adb      string
	serial   string
	output   string
	interval time.Duration
	duration time.Duration
}

type record struct {
	Schema                   int      `json:"schema"`
	Timestamp                string   `json:"timestamp"`
	Serial                   string   `json:"serial"`
	Kind                     string   `json:"kind"`
	Model                    string   `json:"model,omitempty"`
	AndroidRelease           string   `json:"android_release,omitempty"`
	TemperatureTenthsC       *int64   `json:"temperature_tenths_c,omitempty"`
	Status                   *int64   `json:"status,omitempty"`
	Policy                   string   `json:"policy,omitempty"`
	CurrentKHz               *int64   `json:"current_khz,omitempty"`
	MaxKHz                   *int64   `json:"max_khz,omitempty"`
	HardwareMaxKHz           *int64   `json:"hardware_max_khz,omitempty"`
	MaxToHardwareRatio       *float64 `json:"max_to_hardware_ratio,omitempty"`
	Zone                     string   `json:"zone,omitempty"`
	Name                     string   `json:"name,omitempty"`
	TemperatureMilliDegreesC *int64   `json:"temperature_millidegrees_c,omitempty"`
	Type                     *int64   `json:"type,omitempty"`
	Value                    *int64   `json:"value,omitempty"`
	UnavailableFields        []string `json:"unavailable_fields,omitempty"`
}

type runner interface {
	run(ctx context.Context, args ...string) (string, error)
}

type commandRunner struct {
	path string
}

func (r commandRunner) run(ctx context.Context, args ...string) (string, error) {
	output, err := exec.CommandContext(ctx, r.path, args...).CombinedOutput()
	if err != nil {
		return "", fmt.Errorf("adb %s: %w: %s", strings.Join(args, " "), err, strings.TrimSpace(string(output)))
	}
	return strings.TrimSpace(strings.ReplaceAll(string(output), "\r", "")), nil
}

func main() {
	if err := run(context.Background(), os.Args[1:], os.Stdout, os.Stderr); err != nil {
		fmt.Fprintf(os.Stderr, "android-device-telemetry: %v\n", err)
		os.Exit(1)
	}
}

func run(ctx context.Context, args []string, stdout, stderr io.Writer) error {
	opts, err := parseOptions(args, stderr)
	if err != nil {
		return err
	}
	adbPath, err := exec.LookPath(opts.adb)
	if err != nil {
		return fmt.Errorf("locate adb %q: %w", opts.adb, err)
	}
	adb := commandRunner{path: adbPath}
	serial, err := resolveSerial(ctx, adb, opts.serial)
	if err != nil {
		return err
	}
	state, err := adb.run(ctx, "-s", serial, "get-state")
	if err != nil || state != "device" {
		return fmt.Errorf("device is not online: %s", serial)
	}

	output := stdout
	var file *os.File
	if opts.output != "" {
		if err := os.MkdirAll(filepath.Dir(opts.output), 0o755); err != nil {
			return err
		}
		file, err = os.Create(opts.output)
		if err != nil {
			return err
		}
		defer file.Close()
		output = file
	}
	encoder := json.NewEncoder(output)
	collect := func() error {
		records, collectErr := collectSnapshot(ctx, adb, serial, time.Now().UTC())
		if collectErr != nil {
			return collectErr
		}
		for _, item := range records {
			if err := encoder.Encode(item); err != nil {
				return err
			}
		}
		return nil
	}
	if opts.mode == "snapshot" {
		return collect()
	}
	return monitorSnapshots(
		ctx,
		opts.duration,
		opts.interval,
		collect,
		time.Now,
		waitContext,
	)
}

func monitorSnapshots(
	ctx context.Context,
	duration time.Duration,
	interval time.Duration,
	collect func() error,
	now func() time.Time,
	wait func(context.Context, time.Duration) error,
) error {
	started := now()
	for {
		if err := collect(); err != nil {
			return err
		}
		elapsed := now().Sub(started)
		if elapsed >= duration {
			return nil
		}
		delay := interval
		if remaining := duration - elapsed; delay > remaining {
			delay = remaining
		}
		if err := wait(ctx, delay); err != nil {
			return err
		}
	}
}

func waitContext(ctx context.Context, duration time.Duration) error {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

func parseOptions(args []string, stderr io.Writer) (options, error) {
	opts := options{
		mode:     "snapshot",
		adb:      "adb",
		interval: 5 * time.Second,
		duration: 60 * time.Second,
	}
	if len(args) > 0 && (args[0] == "snapshot" || args[0] == "monitor") {
		opts.mode = args[0]
		args = args[1:]
	}
	flags := flag.NewFlagSet("android-device-telemetry", flag.ContinueOnError)
	flags.SetOutput(stderr)
	flags.StringVar(&opts.adb, "adb", opts.adb, "adb executable")
	flags.StringVar(&opts.serial, "serial", "", "target serial; defaults to the single online device")
	flags.StringVar(&opts.output, "output", "", "JSONL output path; defaults to stdout")
	interval := flags.Int("interval-seconds", int(opts.interval/time.Second), "monitor sampling interval")
	duration := flags.Int("duration-seconds", int(opts.duration/time.Second), "bounded monitor duration")
	if err := flags.Parse(args); err != nil {
		return options{}, err
	}
	if flags.NArg() != 0 {
		return options{}, fmt.Errorf("unexpected arguments: %s", strings.Join(flags.Args(), " "))
	}
	if *interval <= 0 || *duration <= 0 {
		return options{}, errors.New("interval and duration must be positive")
	}
	opts.interval = time.Duration(*interval) * time.Second
	opts.duration = time.Duration(*duration) * time.Second
	return opts, nil
}

func resolveSerial(ctx context.Context, adb runner, requested string) (string, error) {
	if requested != "" {
		return requested, nil
	}
	output, err := adb.run(ctx, "devices")
	if err != nil {
		return "", err
	}
	var online []string
	scanner := bufio.NewScanner(strings.NewReader(output))
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) == 2 && fields[1] == "device" {
			online = append(online, fields[0])
		}
	}
	if len(online) != 1 {
		return "", fmt.Errorf("expected one online device, found %d", len(online))
	}
	return online[0], scanner.Err()
}

func collectSnapshot(ctx context.Context, adb runner, serial string, now time.Time) ([]record, error) {
	run := func(args ...string) (string, error) {
		return adb.run(ctx, append([]string{"-s", serial}, args...)...)
	}
	model, err := run("shell", "getprop", "ro.product.model")
	if err != nil {
		return nil, err
	}
	release, err := run("shell", "getprop", "ro.build.version.release")
	if err != nil {
		return nil, err
	}
	battery, err := run("shell", "dumpsys", "battery")
	if err != nil {
		return nil, err
	}
	thermal, err := run("shell", "dumpsys", "thermalservice")
	if err != nil {
		return nil, err
	}
	policies, err := run("shell", cpuPolicyProbe)
	if err != nil {
		return nil, err
	}
	zones, err := run("shell", thermalZoneProbe)
	if err != nil {
		return nil, err
	}

	timestamp := now.Format(time.RFC3339)
	base := func(kind string) record {
		return record{Schema: schemaVersion, Timestamp: timestamp, Serial: serial, Kind: kind}
	}
	device := base("device")
	device.Model = model
	device.AndroidRelease = release
	result := []record{device}

	batteryRecord := base("battery")
	batteryRecord.TemperatureTenthsC = parseNamedInt(battery, "temperature")
	if batteryRecord.TemperatureTenthsC == nil {
		batteryRecord.UnavailableFields = append(
			batteryRecord.UnavailableFields,
			"temperature_tenths_c",
		)
	}
	result = append(result, batteryRecord)

	statusRecord := base("thermal_status")
	statusRecord.Status = parseNamedInt(thermal, "Thermal Status")
	if statusRecord.Status == nil {
		statusRecord.UnavailableFields = append(statusRecord.UnavailableFields, "status")
	}
	result = append(result, statusRecord)

	for _, fields := range parseTSV(policies, 4) {
		item := base("cpu_policy")
		item.Policy = fields[0]
		item.CurrentKHz = parseInt(fields[1])
		item.MaxKHz = parseInt(fields[2])
		item.HardwareMaxKHz = parseInt(fields[3])
		item.UnavailableFields = missingFields(
			namedInt{name: "current_khz", value: item.CurrentKHz},
			namedInt{name: "max_khz", value: item.MaxKHz},
			namedInt{name: "hardware_max_khz", value: item.HardwareMaxKHz},
		)
		if item.MaxKHz != nil && item.HardwareMaxKHz != nil && *item.HardwareMaxKHz > 0 {
			ratio := float64(*item.MaxKHz) / float64(*item.HardwareMaxKHz)
			item.MaxToHardwareRatio = &ratio
		}
		result = append(result, item)
	}
	for _, fields := range parseTSV(zones, 3) {
		item := base("thermal_zone")
		item.Zone = fields[0]
		item.Name = fields[1]
		item.TemperatureMilliDegreesC = parseInt(fields[2])
		if item.TemperatureMilliDegreesC == nil {
			item.UnavailableFields = append(
				item.UnavailableFields,
				"temperature_millidegrees_c",
			)
		}
		result = append(result, item)
	}
	result = append(result, parseCoolingDevices(thermal, base)...)
	return result, nil
}

func parseNamedInt(text, key string) *int64 {
	for _, line := range strings.Split(text, "\n") {
		parts := strings.SplitN(line, ":", 2)
		if len(parts) == 2 && strings.TrimSpace(parts[0]) == key {
			return parseInt(strings.TrimSpace(parts[1]))
		}
	}
	return nil
}

func parseInt(value string) *int64 {
	parsed, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64)
	if err != nil {
		return nil
	}
	return &parsed
}

func parseTSV(text string, width int) [][]string {
	var rows [][]string
	for _, line := range strings.Split(text, "\n") {
		line = strings.TrimSuffix(line, "\r")
		if strings.TrimSpace(line) == "" {
			continue
		}
		fields := strings.Split(line, "\t")
		if len(fields) == width {
			for index := range fields {
				fields[index] = strings.TrimSpace(fields[index])
			}
			rows = append(rows, fields)
		}
	}
	return rows
}

type namedInt struct {
	name  string
	value *int64
}

func missingFields(fields ...namedInt) []string {
	var missing []string
	for _, field := range fields {
		if field.value == nil {
			missing = append(missing, field.name)
		}
	}
	return missing
}

var coolingPattern = regexp.MustCompile(
	`CoolingDevice\{mValue=(-?[0-9]+), mType=(-?[0-9]+), mName=([^}]+)\}`,
)

func parseCoolingDevices(text string, base func(string) record) []record {
	var result []record
	for _, match := range coolingPattern.FindAllStringSubmatch(text, -1) {
		item := base("cooling_device")
		item.Value = parseInt(match[1])
		item.Type = parseInt(match[2])
		item.Name = match[3]
		result = append(result, item)
	}
	return result
}

const cpuPolicyProbe = `
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    name=${policy##*/}
    current=$(cat "$policy/scaling_cur_freq" 2>/dev/null)
    maximum=$(cat "$policy/scaling_max_freq" 2>/dev/null)
    hardware=$(cat "$policy/cpuinfo_max_freq" 2>/dev/null)
    printf "%s\t%s\t%s\t%s\n" "$name" "$current" "$maximum" "$hardware"
done`

const thermalZoneProbe = `
for zone in /sys/class/thermal/thermal_zone*; do
    [ -d "$zone" ] || continue
    base=${zone##*/}
    name=$(cat "$zone/type" 2>/dev/null)
    temperature=$(cat "$zone/temp" 2>/dev/null)
    printf "%s\t%s\t%s\n" "$base" "$name" "$temperature"
done`
