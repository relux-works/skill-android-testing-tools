package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

type fakeRunner struct {
	responses map[string]string
}

func (f fakeRunner) run(_ context.Context, args ...string) (string, error) {
	key := strings.Join(args, "\x00")
	value, ok := f.responses[key]
	if !ok {
		return "", &unexpectedCommand{command: strings.Join(args, " ")}
	}
	return value, nil
}

type unexpectedCommand struct {
	command string
}

func (e *unexpectedCommand) Error() string {
	return "unexpected command: " + e.command
}

func TestCollectSnapshot(t *testing.T) {
	serial := "serial-1"
	adb := fakeRunner{responses: map[string]string{
		"-s\x00serial-1\x00shell\x00getprop\x00ro.product.model":         "Mock Phone",
		"-s\x00serial-1\x00shell\x00getprop\x00ro.build.version.release": "13",
		"-s\x00serial-1\x00shell\x00dumpsys\x00battery":                  "  temperature: 391",
		"-s\x00serial-1\x00shell\x00dumpsys\x00thermalservice": "" +
			"Thermal Status: 0\n" +
			"CoolingDevice{mValue=4, mType=2, mName=thermal-cpufreq-6}",
		"-s\x00serial-1\x00shell\x00" + cpuPolicyProbe: "" +
			"policy0\t1804800\t1804800\t1804800\n" +
			"policy6\t1939200\t1939200\t2304000",
		"-s\x00serial-1\x00shell\x00" + thermalZoneProbe: "" +
			"thermal_zone42\tlmh-dcvs-00\t75000",
	}}

	records, err := collectSnapshot(
		context.Background(),
		adb,
		serial,
		time.Date(2026, 7, 30, 14, 35, 58, 0, time.UTC),
	)
	if err != nil {
		t.Fatal(err)
	}

	requireRecord(t, records, "battery", func(item record) bool {
		return item.TemperatureTenthsC != nil && *item.TemperatureTenthsC == 391
	})
	requireRecord(t, records, "cpu_policy", func(item record) bool {
		return item.Policy == "policy6" &&
			item.MaxKHz != nil && *item.MaxKHz == 1939200 &&
			item.HardwareMaxKHz != nil && *item.HardwareMaxKHz == 2304000 &&
			item.MaxToHardwareRatio != nil && *item.MaxToHardwareRatio < 1
	})
	requireRecord(t, records, "cooling_device", func(item record) bool {
		return item.Name == "thermal-cpufreq-6" &&
			item.Value != nil && *item.Value == 4
	})
	requireRecord(t, records, "thermal_zone", func(item record) bool {
		return item.Name == "lmh-dcvs-00" &&
			item.TemperatureMilliDegreesC != nil &&
			*item.TemperatureMilliDegreesC == 75000
	})

	for _, item := range records {
		if _, err := json.Marshal(item); err != nil {
			t.Fatalf("marshal %s: %v", item.Kind, err)
		}
	}
}

func TestCollectSnapshotKeepsRowsWithUnavailableTrailingFields(t *testing.T) {
	serial := "serial-1"
	adb := fakeRunner{responses: map[string]string{
		"-s\x00serial-1\x00shell\x00getprop\x00ro.product.model":         "Mock Phone",
		"-s\x00serial-1\x00shell\x00getprop\x00ro.build.version.release": "13",
		"-s\x00serial-1\x00shell\x00dumpsys\x00battery":                  "temperature: unavailable",
		"-s\x00serial-1\x00shell\x00dumpsys\x00thermalservice":           "Thermal Status: unavailable",
		"-s\x00serial-1\x00shell\x00" + cpuPolicyProbe:                   "policy6\t1939200\t1939200\t",
		"-s\x00serial-1\x00shell\x00" + thermalZoneProbe:                 "thermal_zone42\tlmh-dcvs-00\t",
	}}

	records, err := collectSnapshot(
		context.Background(),
		adb,
		serial,
		time.Date(2026, 7, 30, 14, 35, 58, 0, time.UTC),
	)
	if err != nil {
		t.Fatal(err)
	}

	requireRecord(t, records, "cpu_policy", func(item record) bool {
		return item.Policy == "policy6" &&
			item.CurrentKHz != nil &&
			item.MaxKHz != nil &&
			item.HardwareMaxKHz == nil &&
			contains(item.UnavailableFields, "hardware_max_khz")
	})
	requireRecord(t, records, "thermal_zone", func(item record) bool {
		return item.Name == "lmh-dcvs-00" &&
			item.TemperatureMilliDegreesC == nil &&
			contains(item.UnavailableFields, "temperature_millidegrees_c")
	})
	requireRecord(t, records, "battery", func(item record) bool {
		return contains(item.UnavailableFields, "temperature_tenths_c")
	})
	requireRecord(t, records, "thermal_status", func(item record) bool {
		return contains(item.UnavailableFields, "status")
	})
}

func TestResolveSerial(t *testing.T) {
	adb := fakeRunner{responses: map[string]string{
		"devices": "List of devices attached\nserial-1\tdevice\noffline-1\toffline",
	}}
	serial, err := resolveSerial(context.Background(), adb, "")
	if err != nil {
		t.Fatal(err)
	}
	if serial != "serial-1" {
		t.Fatalf("serial = %q", serial)
	}
}

func TestParseOptionsRejectsUnboundedValues(t *testing.T) {
	if _, err := parseOptions(
		[]string{"monitor", "--duration-seconds", "0"},
		&strings.Builder{},
	); err == nil {
		t.Fatal("expected duration validation error")
	}
}

func TestMonitorSnapshotsUsesBoundedCadence(t *testing.T) {
	current := time.Unix(100, 0)
	collections := 0
	err := monitorSnapshots(
		context.Background(),
		10*time.Second,
		4*time.Second,
		func() error {
			collections++
			return nil
		},
		func() time.Time {
			return current
		},
		func(_ context.Context, duration time.Duration) error {
			current = current.Add(duration)
			return nil
		},
	)
	if err != nil {
		t.Fatal(err)
	}
	if collections != 4 {
		t.Fatalf("collections = %d, want 4", collections)
	}
	if current != time.Unix(110, 0) {
		t.Fatalf("finished at %s", current)
	}
}

func TestRunSnapshotWithCommandADB(t *testing.T) {
	adbPath := writeFakeADB(t)
	var stdout bytes.Buffer
	var stderr bytes.Buffer
	if err := run(
		context.Background(),
		[]string{"snapshot", "--adb", adbPath, "--serial", "serial-1"},
		&stdout,
		&stderr,
	); err != nil {
		t.Fatalf("run: %v; stderr=%s", err, stderr.String())
	}

	decoder := json.NewDecoder(&stdout)
	var records []record
	for {
		var item record
		err := decoder.Decode(&item)
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		records = append(records, item)
	}
	requireRecord(t, records, "cpu_policy", func(item record) bool {
		return item.Policy == "policy6" &&
			item.MaxToHardwareRatio != nil &&
			*item.MaxToHardwareRatio < 1
	})

	outputPath := filepath.Join(t.TempDir(), "nested", "snapshot.jsonl")
	if err := run(
		context.Background(),
		[]string{"snapshot", "--adb", adbPath, "--output", outputPath},
		&bytes.Buffer{},
		&stderr,
	); err != nil {
		t.Fatalf("run with auto serial and output: %v", err)
	}
	output, err := os.ReadFile(outputPath)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Contains(output, []byte(`"kind":"battery"`)) {
		t.Fatalf("output is missing battery record: %s", output)
	}
}

func TestLauncherPreservesCallerRelativeOutput(t *testing.T) {
	adbPath := writeFakeADB(t)
	launcher, err := filepath.Abs(filepath.Join("..", "..", "scripts", "android-device-telemetry"))
	if err != nil {
		t.Fatal(err)
	}
	callerDirectory := t.TempDir()
	command := exec.Command(
		launcher,
		"snapshot",
		"--adb",
		adbPath,
		"--serial",
		"serial-1",
		"--output",
		filepath.Join("evidence", "snapshot.jsonl"),
	)
	command.Dir = callerDirectory
	output, err := command.CombinedOutput()
	if err != nil {
		t.Fatalf("launcher: %v: %s", err, output)
	}
	resultPath := filepath.Join(callerDirectory, "evidence", "snapshot.jsonl")
	result, err := os.ReadFile(resultPath)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Contains(result, []byte(`"kind":"cpu_policy"`)) {
		t.Fatalf("launcher output is missing cpu policy: %s", result)
	}
}

func writeFakeADB(t *testing.T) string {
	t.Helper()
	adbPath := filepath.Join(t.TempDir(), "adb")
	script := `#!/usr/bin/env bash
set -euo pipefail
command_text="$*"
case "$command_text" in
  "devices") printf 'List of devices attached\nserial-1\tdevice\n' ;;
  "-s serial-1 get-state") printf 'device\n' ;;
  "-s serial-1 shell getprop ro.product.model") printf 'Mock Phone\n' ;;
  "-s serial-1 shell getprop ro.build.version.release") printf '13\n' ;;
  "-s serial-1 shell dumpsys battery") printf 'temperature: 390\n' ;;
  "-s serial-1 shell dumpsys thermalservice")
    printf 'Thermal Status: 0\nCoolingDevice{mValue=2, mType=2, mName=thermal-cpufreq-6}\n'
    ;;
  *"/sys/devices/system/cpu/cpufreq/policy"*)
    printf 'policy6\t1939200\t1939200\t2304000\n'
    ;;
  *"/sys/class/thermal/thermal_zone"*)
    printf 'thermal_zone0\tbattery\t39000\n'
    ;;
  *) printf 'unexpected: %s\n' "$command_text" >&2; exit 9 ;;
esac
`
	if err := os.WriteFile(adbPath, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	return adbPath
}

func requireRecord(
	t *testing.T,
	records []record,
	kind string,
	matches func(record) bool,
) {
	t.Helper()
	for _, item := range records {
		if item.Kind == kind && matches(item) {
			return
		}
	}
	t.Fatalf("missing matching %s record: %+v", kind, records)
}

func contains(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}
