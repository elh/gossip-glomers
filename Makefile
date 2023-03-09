.PHONY: run-1 test lint wc check-env

run-1: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w echo --bin src/1-echo.clj --node-count 1 --time-limit 10

test:
	@clj -X:test

lint:
	@clj -M:lint

wc:
	@find -s src/* | xargs wc

check-maelstrom:
ifndef MAELSTROM_PATH
	$(error MAELSTROM_PATH is undefined)
endif
