.PHONY: run-1 run-2 run-3a run-3b run-3c run-3d run-3e run-4 run-5a run-5b run-5c run-6a run-6b run-6c run test lint wc check-env

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

run-4: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w g-counter --bin src/4_grow_only_counter.clj --node-count 3 --rate 100 --time-limit 20 --nemesis partition

run-5a: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w kafka --bin src/5a_single_node_kafka.clj --node-count 1 --concurrency 2n --time-limit 20 --rate 1000

run-5b: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w kafka --bin src/5b_multi_node_kafka.clj --node-count 2 --concurrency 2n --time-limit 20 --rate 1000

run-5c: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w kafka --bin src/5c_efficient_kafka.clj --node-count 2 --concurrency 2n --time-limit 20 --rate 1000

run-6a: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w txn-rw-register --bin src/6a_single_node_txs.clj --node-count 1 --time-limit 20 --rate 1000 --concurrency 2n --consistency-models read-uncommitted --availability total

run-6b: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w txn-rw-register --bin src/6b_read_uncommitted_txs.clj --node-count 2 --concurrency 2n --time-limit 20 --rate 1000 --consistency-models read-committed --availability total --nemesis partition

run-6c: check-maelstrom
	@${MAELSTROM_PATH}/maelstrom test -w txn-rw-register --bin src/6c_read_committed_txs.clj --node-count 2 --concurrency 2n --time-limit 20 --rate 1000 --consistency-models read-committed --availability total --nemesis partition

# runs all maelstrom tests
run: run-1 run-2 run-3a run-3b run-3c run-3d run-3e run-4 run-5a run-5b run-5c run-6a run-6b run-6c

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
