.PHONY: run-1 run-2 run test lint wc check-env

run-1: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w echo --bin src/1-echo.clj --node-count 1 --time-limit 10

run-2: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w unique-ids --bin src/2-unique-ids.clj --time-limit 30 --rate 1000 --node-count 3 --availability total --nemesis partition

# runs all maelstrom tests
run: run-1 run-2

maelstrom-serve: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom serve

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
