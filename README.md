clojobuf
=========

clojure(script) library that dynamically interprets protobuf files (.proto) and use the resultant schemas to encode/decode plain clojure(script) map into/from protobuf binaries. Supports proto2, proto3 and edition 2023.

## TL;DR
Add the following to deps.edn (or its equivalent for lein).
```edn
{:deps {com.github.s-expresso/clojobuf {:mvn/version "0.2.1"}}}
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

Code will be like [src/clojobuf/example/ex1.cljc](https://github.com/s-expresso/clojobuf/blob/main/src/clojobuf/example/ex1.cljc)
```clojure
(ns clojobuf.example.ex1
  (:require [clojobuf.core :refer [encode decode find-fault protoc]]))

; :auto-malli-registry and :auto-import both default to true if omitted
(def registry (protoc ["resources/protobuf/"]     ;; paths to look for protobuf files
                      ["example.proto"]           ;; protobuf files to be processed
                      ; :auto-malli-registry true ;; default true; see below for usage
                      ; :auto-import true         ;; default true; automatically load all imports, recursively
                      ))

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
i.e. these 4 functions are all you need: `protoc`, `encode`, `decode` and `find-fault`; though `default-msg` will probably be useful too.

Note: `clojobuf.core/protoc` is only available to clj runtime, for cljs runtime, use:
* `clojobuf.nodejs/protoc` which works for cljs targeting nodejs, or
* `clojobuf.macro/protoc-macro` which works for both clj and cljs

## clojobuf.core/protoc

### Usage
```
(protoc PATHS FILES <KWARGS>)
```
Where
* `PATHS` - a vector of paths
  * example: `["a/b/path1/", "a/b/path2/", "/path/to/google/protobuf/src/"]`
  * each path must have trailing `/` (or `\` on Windows)
* `FILES` - a vector of protobuf files to be processed
  * example: `["file1.proto", "file2.proto" "subdir/file3.proto"]`
  * each file will be looked up in `PATHS` following its declaration order and only the first match will be processed
* `<KWARGS>`
  * `:auto-import` - default `true`; instructs `protoc` to recursively load all imports, supports circular import
  * `:auto-malli-registry` - default `true`; if `false`, validation schema is output as plain malli data, see below for more info

### Output

`protoc` function returns a registry which is passed into `encode`, `decode` and `find-fault` as its first parameter.

You do not need to know the registry's content, but in case you are curious, its details are provided below.

A registry contains 2 schemas: 1 for encoding/decoding and 1 for validation. 

Sample encoding/decoding schema
```clojure
[#:my.pb.ns{:Enum
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
             :encode
             {:msg1 [1 "my.pb.ns/Msg" :optional nil],
              :msg1s [2 "my.pb.ns/Msg" :repeated nil]},
             :decode
             {1 [:msg1 "my.pb.ns/Msg" :optional nil],
              2 [:msg1s "my.pb.ns/Msg" :repeated nil]}}}
```

Sample validation schema
* note you have to invoke `protoc` with optional named arg `:auto-malli-registry false` to get a schema as plain map like below
* and yes this is [malli schema](https://github.com/metosin/malli) :)
```clojure
 #:my.pb.ns{:Enum [:enum :MINUS_ONE :ZERO :ONE],
            :Msg
            [:and
             [:map
              {:closed true}
              [:int32_val {:optional true} :int32]
              [:string_val {:optional true} :string]
              [:bool_val {:optional true} :boolean]
              [:enum_val {:optional true} [:ref :my.pb.ns/Enum]]
              [:either
               {:optional true}
               [:enum :sint32_val :sint64_val]]
              [:sint32_val {:optional true} :sint32]
              [:sint64_val {:optional true} :sint64]
              [:int64_string {:optional true} [:map-of :int64 :string]]
              [:double_vals {:optional true} [:vector :double]]]
             [:oneof :either [:sint32_val :sint64_val]]],
            :Msg2
            [:map
             {:closed true}
             [:msg1 {:optional true} [:ref :my.pb.ns/Msg]]
             [:msg1s {:optional true} [:vector [:ref :my.pb.ns/Msg]]]]}]
```

Note:
* You can also use `clojobuf.core/->malli-registry` to convert above plain map into a malli registry. See [src/clojobuf/example/ex2.cljc](https://github.com/s-expresso/clojobuf/blob/main/src/clojobuf/example/ex2.cljc) for a working example.

## clojobuf.nodejs/protoc
`clojobuf.nodejs/protoc` uses NodeJS file system module but otherwise works exactly that same way as `clojobuf.core/protoc`. It is placed in a separate namespace to provide flexibility to require it separately, as file access isn't available to browser runtime.

## clojobuf.macro/protoc-macro
You can use `protoc-macro` to invoke protoc at compile time. For this, `:auto-malli-registry` is hardcoded to `false`, hence use of `->malli-registry` is needed to convert the malli schema into malli registry.
```clj
(ns clojobuf.example.ex3
  (:require [clojobuf.core :refer [->malli-registry]]
            [clojobuf.macro :refer [protoc-macro]]))

(def registry (let [[codec-schema malli-schema] (protoc-macro ["resources/protobuf/"] ["example.proto"])]
                [codec-schema (->malli-registry malli-schema)]))
```
`protoc-macro` works on all runtime, but is especially useful for cljs targeting browser as it doesn't have file system access at runtime. See [src/clojobuf/example/ex3.cljc](https://github.com/s-expresso/clojobuf/blob/main/src/clojobuf/example/ex3.cljc) for a working example.


## clojobuf.core/encode

### Usage
```
(clojobuf.core/encode <REGISTRY> <MSG-ID> <MSG>)
```
where
* `REGISTRY` - output of `protoc`
* `MSG-ID` - message id, e.g. `:my.pb.ns/Msg`
* `MSG` - map like messaage corresponding to the message schema, note the following on field presence:
  * required: if field is unset, encoding fails and nil is retured
  * optional: if field is unset, it will not be serialized
  * implicit: if field is unset or same as default value, it will not be serialized
* returns encoded binary, or `nil` for error case

## clojobuf.core/decode

### Usage
```
(clojobuf.core/decode <REGISTRY> <MSG-ID> <BIN>)
```
where
* `REGISTRY` - output of `protoc`
* `MSG-ID` - message id, e.g. `:my.pb.ns/Msg`
* `BIN` - binary to be decoded, note the following on field presence:
  * required: if absent in binary, decoding fails and nil is returned
  * optional: if absent in binary, field will be nil
  * implicit: if absent in binary, field will be dault value
* returns decoded message, or `nil` for error case

### Unknown Fields
During decode, unknown fields are placed into `:?` as a vector of 3 values (field number, wire type, wire value).

```clojure
{:normal_field 1
 :? [[2 0 123] ; field number: 2, wire-type: 0, value: 123 
     [3 1 456] ; field number: 3, wire-type: 1, value: 456
     ]}
```

## clojobuf.core/find-fault

### Usage
```
(clojobuf.core/find-fault <REGISTRY> <MSG-ID> <MSG>)
```
where
* `REGISTRY` - output of `protoc`
* `MSG-ID` - message id, e.g. `:my.pb.ns/Msg`
* `MSG` - map like messaage corresponding to the message schema
* returns map like object explaining the fault(s) found, or `nil` if none found.

## clojobuf.core/default-msg

### Usage
```
(clojure.core/default-msg <REGISTRY> <MSG-ID>)
```
where
* `REGISTRY` - output of `protoc`
* `MSG-ID` - message id, e.g. `:my.pb.ns/Msg`
* returns a message containing all its fields:
  * required field: default value, or `nil` for message type
  * optional field: `nil`
  * implicit field: default value
  * repeated field: empty `[]`
  * oneof field: `nil`
  * map field: `{}`


## Related Protobuf Projects
* https://github.com/s-expresso/rubberbuf
* https://github.com/s-expresso/clojobuf-codec
