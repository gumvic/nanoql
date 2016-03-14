# Change Log

## [0.3.4] - 2016-03-14
### Changed
- Switch to clojure 1.8.0
- Query operation functions are now lazy
- Query operation functions' arguments are now readable (not destructured)

## [0.3.3] - 2016-03-11
### Changed
- **difference** semantics

## [0.3.2] - 2016-03-11
### Changed
- **execute** will retain the query structure in all cases (refer to "should repeat the query structure no matter what" subtest)
- **execute** allows dynamic and deferred nodes on any level

## [0.3.1] - 2016-03-07
### Added
- Optional **env** parameter for **execute**

### Changed
- **execute** won't recursively query nils
- **execute** allows dynamic and deferred nodes only on the first level of a static node

## [0.3.0] - 2016-03-04
### Changed
- Switched from channels to promises
- Changed **execute** args from (node, query) to (query, node)
- Updated README

## [0.2.0] - 2016-03-02
### Added
- Query schema
- Query-Def schema
- async execution
- README