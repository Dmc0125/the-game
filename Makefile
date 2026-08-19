-include Makefile.local

.PHONY: run

run:
	./scripts/run.sh

run-debug:
	./scripts/run.sh --log-debug

adb-pair:
	adb pair $(DEVICE_IP):$(PORT)

adb-connect:
	adb connect $(DEVICE_IP):$(PORT)
