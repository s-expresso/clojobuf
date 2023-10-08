clojobuf
=========

clojure(script) library that dynamically interprets protobuf files (.proto) and use the resultant schemas to encode/decode plain clojure(script) map into/from protobuf binaries. Supports both proto2 and proto3.

## Usage
Add the following to deps.edn (or its equivalent for lein).
```edn
{:deps
 s-expresso/clojobuf {:git/url "https://github.com/s-expresso/clojobuf.git"
                            :git/sha "267569de92fabb32c35092a87969c997913221d6"
                            :git/tag "v0.1.4"}}
```

Say you have the following `example.proto` file
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
    optional Msg msg1  = 1;
    repeated Msg msg1s = 2;
}
```

Code will be like
```clojure
(ns clojobuf.example.ex1
  (:require [clojobuf.core :refer [encode decode find-fault protoc]]))

(def registry (protoc ["resources/protobuf/"] ["example.proto"]))

(def msg {:int32_val -1,
          :string_val "abc",
          :bool_val false,
          :enum_val :ZERO,
          :either :sint32_val,
          :sint32_val -1
          :int64_string {1 "abc", 2 "def"}
          :double_vals [0.0, 1.0, 2.0]})

(def msg2 {:msg1 msg, :msg1s [msg, msg]})

;-------------------------------------------------------------------
; Success case
;-------------------------------------------------------------------
(def binary (encode registry :my.pb.ns/Msg msg))
(decode registry :my.pb.ns/Msg binary)
; => msg

(def binary2 (encode registry :my.pb.ns/Msg2 msg2))
(decode registry :my.pb.ns/Msg2 binary2)
; => msg2

;-------------------------------------------------------------------
; Error case
;-------------------------------------------------------------------
(let [bin (encode registry :my.pb.ns/Msg {:this-field-doesnt-exists 0})]
  (when (nil? bin)
    (find-fault registry :my.pb.ns/Msg {:this-field-doesnt-exists 0})))
; => {:this-field-doesnt-exists ["disallowed key"]}
```
i.e. these 4 functions are all you need: `protoc`, `encode`, `decode` and `find-fault`

## protoc
`protoc` function generates 2 schemas which are plain maps; 1 for encoding/decoding and 1 for validation.

Sample encoding/decoding schema
```clojure
#:my.pb.ns{:Enum
           {:syntax :proto2,
            :type :enum,
            :default :MINUS_ONE,
            :encode {:MINUS_ONE -1, :ZERO 0, :ONE 1},
            :decode {-1 :MINUS_ONE, 0 :ZERO, 1 :ONE}},
           :Msg
           {:syntax :proto2,
            :type :msg,
            :encode
            {:string_val [2 :string :optional nil],
             :sint32_val [5 :sint32 :oneof nil],
             :double_vals [8 :double :repeated nil],
             :sint64_val [6 :sint64 :oneof nil],
             :bool_val [3 :bool :optional nil],
             :int64_string [7 :map [:int64 :string] nil],
             :enum_val [4 "my.pb.ns/Enum" :optional nil],
             :int32_val [1 :int32 :optional nil],
             :either [:oneof :sint32_val :sint64_val]},
            :decode
            {1 [:int32_val :int32 :optional nil],
             2 [:string_val :string :optional nil],
             3 [:bool_val :bool :optional nil],
             4 [:enum_val "my.pb.ns/Enum" :optional nil],
             5 [:sint32_val :sint32 [:oneof :either] nil],
             6 [:sint64_val :sint64 [:oneof :either] nil],
             7 [:int64_string :map [:int64 :string] nil],
             8 [:double_vals :double :repeated nil]}},
           :Msg2
           {:syntax :proto2,
            :type :msg,
            :encode {:msg1 [1 "my.pb.ns/Msg" :optional nil], :msg1s [2 "my.pb.ns/Msg" :repeated nil]},
            :decode {1 [:msg1 "my.pb.ns/Msg" :optional nil], 2 [:msg1s "my.pb.ns/Msg" :repeated nil]}}}
```

Sample validation schema
* note you have to invoke `protoc` with optional named arg `:malli-composite-registry false` to get a plain schema as map like below
* and yes this is [malli schema](https://github.com/metosin/malli) :)
```clojure
{:my.ns.enum/Enum [:enum :MINUS_ONE :ZERO :ONE :TWO :THREE :FOUR :FIVE],
 :my.ns.nested/Msg1
 [:map
  {:closed true}
  [:enum {:optional true} [:ref :my.ns.enum/Enum]]
  [:nested2 {:optional true} [:ref :my.ns.nested/Msg1.Msg2]]
  [:nested3 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3]]
  [:nested4 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
  [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]],
 :my.ns.singular/Singular
 [:map
  {:closed true}
  [:int32_val {:optional true} int?]
  [:int64_val {:optional true} int?]
  [:uint32_val {:optional true} int?]
  [:uint64_val {:optional true} int?]
  [:sint32_val {:optional true} int?]
  [:sint64_val {:optional true} int?]
  [:bool_val {:optional true} boolean?]
  [:enum_val {:optional true} [:ref :my.ns.enum/Enum]]
  [:fixed64_val {:optional true} int?]
  [:sfixed64_val {:optional true} int?]
  [:double_val {:optional true} double?]
  [:string_val {:optional true} string?]
  [:bytes_val {:optional true} bytes?]
  [:fixed32_val {:optional true} int?]
  [:sfixed32_val {:optional true} int?]
  [:float_val {:optional true} float?]]}
```

## Unknown Fields
During decode, unknown fields are placed into `:?` as a vector of 3 values (field number, wire type, wire value).

```clojure
{:normal_field 1
 :? [[2 0 123] ; field number: 2, wire-type: 0, value: 123 
     [3 1 456] ; field number: 3, wire-type: 1, value: 456
     ]}
```

## Related Protobuf Projects
* https://github.com/s-expresso/rubberbuf
* https://github.com/s-expresso/clojobuf-codec
