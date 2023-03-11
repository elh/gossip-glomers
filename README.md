# gossip-glomers 🙊

Examples:
```bash
# jepsen-io/maelstrom must be installed and MAELSTROM_PATH env var must be set to point to it
# Run a specific challenge
make run-1
make run-2
# ...
make run-5a

# Run all challenges
make run
```

References:
* [Kyle Kingsbury: An Introduction to Distributed Systems (aphyr/distsys-class)](https://github.com/aphyr/distsys-class)
* [Martin Fowler: Gossip Dissemination](https://martinfowler.com/articles/patterns-of-distributed-systems/gossip-dissemination.html)
* [Wikipedia: Conflict-free replicated data type > G-Counter (Grow-only Counter)](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type#G-Counter_(Grow-only_Counter))
* [Wikipedia: Version vector](https://en.wikipedia.org/wiki/Version_vector)
* [Tyler Treat: Building a Distributed Log from Scratch, Part 2: Data Replication](https://bravenewgeek.com/building-a-distributed-log-from-scratch-part-2-data-replication/)
