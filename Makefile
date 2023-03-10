.PHONY: run-1 run-2 run-3a run-3b run-3c run-3d run-3e run test lint wc check-env

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

# Requirements:
# * Messages-per-operation is below 30
# * Median latency is below 400ms
# * Maximum latency is below 600ms
run-3d: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w broadcast --bin src/3d_efficient_broadcast.clj --node-count 25 --time-limit 20 --rate 100 --latency 100

# Requirements:
# * Messages-per-operation is below 20
# * Median latency is below 1s
# * Maximum latency is below 2s
run-3e: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w broadcast --bin src/3e_efficient_broadcast_p2.clj --node-count 25 --time-limit 20 --rate 100 --latency 100

# runs all maelstrom tests
run: run-1 run-2 run-3a run-3b run-3c run-3d run-3e

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
