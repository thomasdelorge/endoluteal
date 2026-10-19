# ==============================================================================
# Endoluteal - Build & Release Automation
# ==============================================================================

# Extract version metadata from app/build.gradle.kts
VERSION      := $(shell grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
VERSION_CODE := $(shell grep -oP 'versionCode\s*=\s*\K[0-9]+' app/build.gradle.kts)
COMMIT_HASH  := $(shell git rev-parse HEAD 2>/dev/null)
BRANCH       := $(shell git rev-parse --abbrev-ref HEAD 2>/dev/null)

APP_ID       := io.github.thomasdelorge.endoluteal
APP_ID_DEV   := $(APP_ID).dev
MAIN_ACTIVITY := fr.luteal.app.MainActivity
AVD          ?= endoluteal_api35
AVD_IMAGE    := system-images;android-35;google_apis;x86_64

# Locate apksigner in Android SDK or system PATH
APKSIGNER    := $(shell which apksigner 2>/dev/null || find $(ANDROID_HOME) $(ANDROID_SDK_ROOT) $(HOME)/.local/share/mise/installs/android-sdk -name apksigner -type f 2>/dev/null | sort -V | tail -n 1)
EMULATOR     := $(shell which emulator 2>/dev/null || find $(ANDROID_HOME) $(ANDROID_SDK_ROOT) $(HOME)/.local/share/mise/installs/android-sdk -name emulator -type f 2>/dev/null | head -n 1)
AVDMANAGER   := $(shell which avdmanager 2>/dev/null || find $(ANDROID_HOME) $(ANDROID_SDK_ROOT) $(HOME)/.local/share/mise/installs/android-sdk -path '*/avdmanager' -type f 2>/dev/null | head -n 1)

DIST_DIR     := dist
RELEASE_APK  := $(DIST_DIR)/endoluteal-v$(VERSION).apk
ADB          ?= adb
DEVICE       ?=
ANCHOR_DATE  ?= $(shell date -u +%F)

.PHONY: help dev dev-install avd emu run logcat demo-backup dev-seed release-seed release-build release-check bump test lint clean

help:
	@echo "Endoluteal Build & Release Targets:"
	@echo "  make dev             - Build debug APK ($(APP_ID_DEV))"
	@echo "  make dev-install     - Build and install debug APK onto connected device"
	@echo "  make avd             - Create AVD $(AVD) if missing"
	@echo "  make emu             - Start emulator $(AVD)"
	@echo "  make run             - installDebug and launch on device/emulator"
	@echo "  make logcat          - Logcat filtered to running app process"
	@echo "  make demo-backup     - Write build/demo-data/endoluteal-demo-backup.json"
	@echo "  make dev-seed        - Demo backup + open import on debug app ($(APP_ID_DEV))"
	@echo "  make release-seed    - Demo backup + open import on release app ($(APP_ID))"
	@echo "  make release-build   - Build signed release APK -> $(RELEASE_APK)"
	@echo "  make release-check   - Verify signature and Git metadata of $(RELEASE_APK)"
	@echo "  make bump VERSION=1.x.y CODE=n - Update versionName and versionCode in build.gradle.kts"
	@echo "  make test            - Run unit test suite"
	@echo "  make lint            - Run Android Lint on debug variant"
	@echo "  make clean           - Clean Gradle build outputs and $(DIST_DIR)/"

# --- Development ---

dev:
	@echo "==> Building debug APK (Endoluteal Dev)..."
	./gradlew assembleDebug

dev-install:
	@echo "==> Installing debug APK on connected device..."
	./gradlew installDebug
	@echo "==> Installed package: $(APP_ID_DEV)"

avd:
	@if [ -d "$(HOME)/.android/avd/$(AVD).avd" ]; then \
		echo "AVD $(AVD) already exists."; \
	else \
		echo "==> Creating AVD $(AVD)..."; \
		echo no | $(AVDMANAGER) create avd -n $(AVD) -k "$(AVD_IMAGE)" -d pixel_8; \
	fi

emu:
	@echo "==> Starting emulator $(AVD)..."
	$(EMULATOR) -avd $(AVD) -gpu host -no-boot-anim

run:
	@echo "==> Installing and launching $(APP_ID_DEV)..."
	./gradlew installDebug
	$(ADB) shell am start -n $(APP_ID_DEV)/$(MAIN_ACTIVITY)

logcat:
	@pid=$$($(ADB) shell pidof -s $(APP_ID_DEV) 2>/dev/null | tr -d '\r'); \
	if [ -z "$$pid" ]; then \
		echo "App $(APP_ID_DEV) is not running."; \
		exit 1; \
	fi; \
	$(ADB) logcat --pid=$$pid

demo-backup:
	@ANCHOR_DATE="$(ANCHOR_DATE)" ./gradlew :app:testDebugUnitTest \
		--tests fr.luteal.demo.DemoBackupExportTest -q
	@echo "==> $(CURDIR)/build/demo-data/endoluteal-demo-backup.json"

dev-seed:
	@bash ./scripts/seed-release.sh "$(ADB)" "$(DEVICE)" "$(APP_ID_DEV)" "$(ANCHOR_DATE)"

release-seed:
	@bash ./scripts/seed-release.sh "$(ADB)" "$(DEVICE)" "$(APP_ID)" "$(ANCHOR_DATE)"

# --- Testing ---

test:
	@echo "==> Running unit tests..."
	./gradlew testDebugUnitTest

lint:
	@echo "==> Running Android Lint..."
	./gradlew lintDebug

# --- Release ---

release-build:
	@echo "==> Checking release prerequisites..."
	@if [ ! -f keystore.properties ]; then \
		echo "ERROR: keystore.properties not found in project root. Cannot produce signed release."; \
		exit 1; \
	fi
	@if ! git diff-index --quiet HEAD --; then \
		echo "ERROR: Working tree has uncommitted tracked changes. Clean working tree required for reproducible release."; \
		exit 1; \
	fi
	@echo "==> Performing clean reproducible build for version $(VERSION) (code $(VERSION_CODE))..."
	./gradlew clean assembleRelease --no-build-cache
	@mkdir -p $(DIST_DIR)
	@cp app/build/outputs/apk/release/app-release.apk $(RELEASE_APK)
	@echo ""
	@echo "==> Release APK generated: $(RELEASE_APK)"
	@echo ""
	@$(MAKE) --no-print-directory release-check

release-check:
	@if [ ! -f "$(RELEASE_APK)" ]; then \
		echo "ERROR: Release APK not found at $(RELEASE_APK)"; \
		exit 1; \
	fi
	@echo "==> Verifying $(RELEASE_APK)..."
	@echo "--- Git Commit in APK ---"
	@unzip -p $(RELEASE_APK) META-INF/version-control-info.textproto 2>/dev/null || echo "No VCS info found."
	@echo "--- APK Signature & Fingerprint ---"
	@if [ -n "$(APKSIGNER)" ] && [ -x "$(APKSIGNER)" ]; then \
		$(APKSIGNER) verify --print-certs $(RELEASE_APK) | grep -E "Signer #|SHA-256"; \
	else \
		echo "apksigner not found; skipping certificate fingerprint output."; \
	fi
	@echo ""
	@echo "================================================================="
	@echo "  Release metadata:"
	@echo "================================================================="
	@echo "    applicationId: $(APP_ID)"
	@echo "    versionName: $(VERSION)"
	@echo "    versionCode: $(VERSION_CODE)"
	@echo "    commit: $(COMMIT_HASH)"
	@echo "================================================================="

# --- Version Bumping ---

bump:
	@if [ -z "$(VERSION)" ] || [ -z "$(CODE)" ]; then \
		echo "Usage: make bump VERSION=1.2.2 CODE=6"; \
		exit 1; \
	fi
	@echo "==> Bumping to versionName=$(VERSION), versionCode=$(CODE)..."
	@sed -i -E 's/versionCode = [0-9]+/versionCode = $(CODE)/' app/build.gradle.kts
	@sed -i -E 's/versionName = "[^"]+"/versionName = "$(VERSION)"/' app/build.gradle.kts
	@echo "==> Updated app/build.gradle.kts."
	@git diff app/build.gradle.kts

# --- Cleaning ---

clean:
	./gradlew clean
	rm -rf $(DIST_DIR)
