# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

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