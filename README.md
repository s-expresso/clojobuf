clojobuf
=========

clojure(script) library that dynamically interprets protobuf files (.proto) and use the resultant schemas to encode/decode plain clojure(script) map into/from protobuf binaries. Supports both proto2 and proto3.

## Usage
Add the following to deps.edn (or its equivalent for lein).
```edn
{:deps
 s-expresso/clojobuf {:git/url "https://github.com/s-expresso/clojobuf.git"
                            :git/sha ""
                            :git/tag ""}}
```

Say you have the following `my.proto` file
```protobuf
syntax = "proto2";
package my.pb.ns;

enum Enum {
    MINUS_ONE = -1;
    ZERO = 0;
    ONE = 1;
}

message Msg {
    optional int32 int32_val        = 1;
    optional string string_val      = 2;
    optional bool bool_val          = 3;
    optional Enum enum_val          = 4;
    oneof either {
        sint32 sint32_val           = 5;
        sint64 sint64_val           = 6;
    }
    map<int64, string> int64_string = 7;
    repeated double double_vals     = 8;
}

message Msg2 {
    optional Msg1 msg1  = 1;
    repeated Msg1 msg1s = 2;
}
```

You can encode/decode like the following
```clojure
(ns my.ns
  (:require [clojobuf.core :refer [encode decode gen-registries]]))


(def codec_malli (gen-registries ["path/"] ["my.proto"]))
; [<codec schemas> <malli schemas>] ; with the latter still WIP

(def codec-schemas (first codec_malli))

(def msg {:int32_val -1,
          :string_val "abc",
          :bool_val false,
          :enum_val :ZERO,
          :either :sint32_val,
          :sint32_val -1
          :int64_string {1 "abc", 2 "def"
          :double_vals [0.0, 1.0, 2.0]}})
(def binary (encode codec-schemas :my.pb.ns/Msg msg))
(decode codec-schemas :my.pb.ns/Msg binary)
; get back a map identical to msg


(def msg2 {:msg1 msg, :msg1s [msg, msg]})
(def binary2 (encode codec-schemas :my.pb.ns/Msg2 msg2))
(decode codec-schemas :my.pb.ns/Msg2 binary2)
; get back a map identical to msg2
```

## Unknown Fields
During decoding, unknown fields are placed into `:?` as a vector of 3 values (field number, wire type, wire value).

```clojure
{:normal_field 1
 :? [[2 0 123] ; field number: 2, wire-type: 0, value: 123 
     ]}
```

## Related Protobuf Projects
* https://github.com/s-expresso/rubberbuf
* https://github.com/s-expresso/clojobuf-codec
