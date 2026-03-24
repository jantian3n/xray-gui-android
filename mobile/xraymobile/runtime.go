package xraymobile

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"sync"

	"github.com/xtls/xray-core/common/platform"
	"github.com/xtls/xray-core/core"
	_ "github.com/xtls/xray-core/main/distro/all"
)

// Runtime is a small gomobile-friendly wrapper around Xray startup.
// Methods return an empty string on success and an error message on failure.
type Runtime struct {
	mu        sync.Mutex
	instance  *core.Instance
	lastError string
}

func NewRuntime() *Runtime {
	return &Runtime{}
}

func Version() string {
	return core.Version()
}

func (r *Runtime) Version() string {
	return core.Version()
}

func (r *Runtime) LastError() string {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.lastError
}

func (r *Runtime) IsRunning() bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.instance != nil && r.instance.IsRunning()
}

func (r *Runtime) ValidateJSON(configJSON string) string {
	return r.validate(configJSON, "", -1)
}

func (r *Runtime) ValidateAndroid(configJSON string, filesDir string) string {
	assetDir, err := prepareAndroidAssetDir(filesDir)
	if err != nil {
		return r.setError(err)
	}
	return r.validate(configJSON, assetDir, -1)
}

func (r *Runtime) ValidateAndroidWithAssetDir(configJSON string, assetDir string) string {
	return r.validate(configJSON, assetDir, -1)
}

func (r *Runtime) StartJSON(configJSON string) string {
	return r.start(configJSON, "", -1)
}

func (r *Runtime) StartAndroid(configJSON string, filesDir string, tunFD int) string {
	assetDir, err := prepareAndroidAssetDir(filesDir)
	if err != nil {
		return r.setError(err)
	}
	return r.start(configJSON, assetDir, tunFD)
}

func (r *Runtime) StartAndroidWithAssetDir(configJSON string, assetDir string, tunFD int) string {
	return r.start(configJSON, assetDir, tunFD)
}

func (r *Runtime) Stop() string {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.instance == nil {
		return r.clearErrorLocked()
	}

	if err := r.instance.Close(); err != nil {
		return r.setErrorLocked(err)
	}

	r.instance = nil
	return r.clearErrorLocked()
}

func (r *Runtime) start(configJSON string, assetDir string, tunFD int) string {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.instance != nil {
		if err := r.instance.Close(); err != nil {
			return r.setErrorLocked(err)
		}
		r.instance = nil
	}

	if err := configureRuntimeEnvironment(assetDir, tunFD); err != nil {
		return r.setErrorLocked(err)
	}

	instance, err := core.StartInstance("json", []byte(configJSON))
	if err != nil {
		return r.setErrorLocked(err)
	}

	r.instance = instance
	return r.clearErrorLocked()
}

func (r *Runtime) validate(configJSON string, assetDir string, tunFD int) string {
	r.mu.Lock()
	defer r.mu.Unlock()

	if err := configureRuntimeEnvironment(assetDir, tunFD); err != nil {
		return r.setErrorLocked(err)
	}

	if _, err := core.LoadConfig("json", bytes.NewReader([]byte(configJSON))); err != nil {
		return r.setErrorLocked(err)
	}

	return r.clearErrorLocked()
}

func prepareAndroidAssetDir(filesDir string) (string, error) {
	if filesDir == "" {
		return "", fmt.Errorf("filesDir is empty")
	}

	runtimeDir := filepath.Join(filesDir, "xray")
	assetDir := filepath.Join(runtimeDir, "geodata")

	if err := os.MkdirAll(assetDir, 0o755); err != nil {
		return "", err
	}

	return assetDir, nil
}

func configureRuntimeEnvironment(assetDir string, tunFD int) error {
	if assetDir != "" {
		if err := os.MkdirAll(assetDir, 0o755); err != nil {
			return err
		}
		if err := os.Setenv(platform.AssetLocation, assetDir); err != nil {
			return err
		}
	}

	if tunFD >= 0 {
		if err := os.Setenv(platform.TunFdKey, strconv.Itoa(tunFD)); err != nil {
			return err
		}
	} else {
		_ = os.Unsetenv(platform.TunFdKey)
	}

	return nil
}

func (r *Runtime) setError(err error) string {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.setErrorLocked(err)
}

func (r *Runtime) setErrorLocked(err error) string {
	if err == nil {
		r.lastError = ""
		return ""
	}
	r.lastError = err.Error()
	return r.lastError
}

func (r *Runtime) clearError() string {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.clearErrorLocked()
}

func (r *Runtime) clearErrorLocked() string {
	r.lastError = ""
	return ""
}
