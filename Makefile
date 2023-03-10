.PHONY: run-1 run-2 run-3a run-3b run-3c run test lint wc check-env

run-1: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w echo --bin src/1_echo.clj --node-count 1 --time-limit 10

run-2: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w unique-ids --bin src/2_unique_ids.clj --time-limit 30 --rate 1000 --node-count 3 --availability total --nemesis partition

run-3a: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w broadcast --bin src/3a_broadcast.clj --node-count 1 --time-limit 20 --rate 10

run-3b: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w broadcast --bin src/3b_multinode_broadcast.clj --node-count 5 --time-limit 20 --rate 10

run-3c: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w broadcast --bin src/3c_fault_tolerant_broadcast.clj --node-count 5 --time-limit 20 --rate 10 --nemesis partition

# runs all maelstrom tests
run: run-1 run-2 run-3a run-3b run-3c

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
