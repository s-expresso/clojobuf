Releases
========

# v0.2.0
* supports protobuf edition 2023
* added `clojobuf.core/default-msg` method for creating a complete message filled based on the following rules:
  * required: default (or `nil` for message field)
  * optional: `nil`
  * implicit: default
  * repeated: `[]`
  * oneof: `nil`
  * map: {}
* allow message field to be `nil` which will not be serialized and will be interpretted as absent during validation
* `clojobuf.core/decode` will validate decoded message and return `nil` if it fails (i.e. missing required field)
* `clojobuf.core/decode` will merge validated decoded message with the default before returning
* use ordered-map for better dev QOL when viewing

# v0.1.12
* release: baseline