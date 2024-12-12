(ns clojobuf.schema-malli-test
  (:require #?(:clj [clojobuf.core :refer [protoc ->malli-registry]])
            #?(:cljs [clojobuf.nodejs :refer [protoc]])
            #?(:cljs [clojobuf.core :refer [->malli-registry]])
            [clojobuf.constant :refer [sint32-max sint32-min sint53-max sint53-min sint64-max sint64-min uint32-max uint32-min uint64-max uint64-min]]
            [clojure.test :refer [is deftest run-tests]]
            [malli.core :as m]
            [malli.transform :as mt]))

(def codec_malli (protoc ["resources/protobuf/"] ["nested.proto",
                                                  "no_package.proto",
                                                  "extension.proto",
                                                  "required.proto", 
                                                  "implicit.proto"]
                         :auto-malli-registry false))
(def malli-schema (second codec_malli))
(def registry (->malli-registry malli-schema))

(deftest test-schema-malli-enum
  (is (= (malli-schema :my.ns.enum/Enum)
         [:enum :MINUS_ONE :ZERO :ONE :TWO :THREE :FOUR :FIVE]))
  (is (= (malli-schema :my.ns.enum/EnumV)
         [:enum :DEFAULT :V1 :V2 :V3 :V1000]))
  (is (true?  (m/validate [:ref :my.ns.enum/Enum] :ZERO registry)))
  (is (false? (m/validate [:ref :my.ns.enum/Enum] :SIX registry)))
  (is (true?  (m/validate [:ref :my.ns.enum/EnumV] :V1000 registry)))
  (is (false? (m/validate [:ref :my.ns.enum/EnumV] :V1001 registry))))

(deftest test-schema-malli-map
  (is (= (malli-schema :my.ns.map/Mappy)
         [:map
          {:closed true}
          [:uint32_sint64 {:optional true} [:map-of :uint32 :sint64]]
          [:int64_string {:optional true} [:map-of :int64 :string]]
          [:fixed32_double {:optional true} [:map-of :fixed32 :double]]
          [:sfixed64_enum {:optional true} [:map-of :sfixed64 [:ref :my.ns.enum/Enum]]]
          [:sint64_singular {:optional true} [:map-of :sint64 [:ref :my.ns.singular/Singular]]]
          [:uint64_packed {:optional true} [:map-of :uint64 [:ref :my.ns.packed/Packed]]]]))
  (is (true?  (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {0 0, 1 -1, 2 2, 3 -3}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {1 1.234}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {1.234 0}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:a :b} registry))))

(deftest test-schema-malli-nested
  (is (= (malli-schema :my.ns.nested/Msg1)
         [:map
          {:closed true}
          [:enum {:optional true} [:ref :my.ns.enum/Enum]]
          [:nested2 {:optional true} [:ref :my.ns.nested/Msg1.Msg2]]
          [:nested3 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3]]
          [:nested4 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]
          [:nested5a {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]
          [:nested5b {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]
          [:nested5c {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2)
         [:map
          {:closed true}
          [:singular {:optional true} [:ref :my.ns.singular/Singular]]
          [:nested3 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3]]
          [:nested4 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3)
         [:map
          {:closed true}
          [:packed {:optional true} [:ref :my.ns.packed/Packed]]
          [:repeat {:optional true} [:ref :my.ns.repeat/Repeat]]
          [:nested4 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4)
         [:map
          {:closed true}
          [:mappy {:optional true} [:ref :my.ns.map/Mappy]]
          [:nested5 {:optional true} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5)
         [:map {:closed true} [:either {:optional true} [:ref :my.ns.oneof/Either]]])))

; TODO compare dynamic function at end of form (currently bypassed with drop-last)
(deftest test-schema-malli-oneof
  (is (= (drop-last (malli-schema :my.ns.oneof/Either))
         [:and
          [:map
           {:closed true}
           [:either
            {:optional true}
            [:enum
             :int32_val
             :int64_val
             :uint32_val
             :uint64_val
             :sint32_val
             :sint64_val
             :bool_val
             :enum_val
             :fixed64_val
             :sfixed64_val
             :double_val
             :string_val
             :bytes_val
             :fixed32_val
             :sfixed32_val
             :float_val
             :singular_msg
             :packed_msg
             :msg_1
             :msg_a]]
           [:int32_val {:optional true} :int32]
           [:int64_val {:optional true} :int64]
           [:uint32_val {:optional true} :uint32]
           [:uint64_val {:optional true} :uint64]
           [:sint32_val {:optional true} :sint32]
           [:sint64_val {:optional true} :sint64]
           [:bool_val {:optional true} :boolean]
           [:enum_val {:optional true} [:ref :my.ns.enum/Enum]]
           [:fixed64_val {:optional true} :fixed64]
           [:sfixed64_val {:optional true} :sfixed64]
           [:double_val {:optional true} :double]
           [:string_val {:optional true} :string]
           [:bytes_val {:optional true} :bytes]
           [:fixed32_val {:optional true} :fixed32]
           [:sfixed32_val {:optional true} :sfixed32]
           [:float_val {:optional true} :double]
           [:singular_msg {:optional true} [:ref :my.ns.singular/Singular]]
           [:packed_msg {:optional true} [:ref :my.ns.packed/Packed]]
           [:msg_1 {:optional true} [:ref :./Msg1]]
           [:msg_a {:optional true} [:ref :./MsgA]]]
          ; TODO compare dynamic function at end of form (currently bypassed with drop-last)
          ]))
  (is (true?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val, :string_val "abc"} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:string_val "abc"} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val, :uint32_val 1} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:a :b} registry))))

(deftest test-schema-malli-packed
  (is (= (malli-schema :my.ns.packed/Packed)
         [:map
          {:closed true}
          [:int32_val {:optional true} [:vector :int32]]
          [:int64_val {:optional true} [:vector :int64]]
          [:uint32_val {:optional true} [:vector :uint32]]
          [:uint64_val {:optional true} [:vector :uint64]]
          [:sint32_val {:optional true} [:vector :sint32]]
          [:sint64_val {:optional true} [:vector :sint64]]
          [:bool_val {:optional true} [:vector :boolean]]
          [:enum_val {:optional true} [:vector [:ref :my.ns.enum/Enum]]]
          [:fixed64_val {:optional true} [:vector :fixed64]]
          [:sfixed64_val {:optional true} [:vector :sfixed64]]
          [:double_val {:optional true} [:vector :double]]
          [:string_val {:optional true} [:vector :string]]
          [:bytes_val {:optional true} [:vector :bytes]]
          [:fixed32_val {:optional true} [:vector :fixed32]]
          [:sfixed32_val {:optional true} [:vector :sfixed32]]
          [:float_val {:optional true} [:vector :double]] ; TODO
          [:singular_msg {:optional true} [:vector [:ref :my.ns.singular/Singular]]]]))
  (is (true?  (m/validate [:ref :my.ns.packed/Packed] {:int32_val [0 1 2 3]} registry)))
  (is (false? (m/validate [:ref :my.ns.packed/Packed] {:a :b} registry))))

(deftest test-schema-malli-repeat
  (is (= (malli-schema :my.ns.repeat/Repeat)
         [:map
          {:closed true}
          [:int32_val {:optional true} [:vector :int32]]
          [:int64_val {:optional true} [:vector :int64]]
          [:uint32_val {:optional true} [:vector :uint32]]
          [:uint64_val {:optional true} [:vector :uint64]]
          [:sint32_val {:optional true} [:vector :sint32]]
          [:sint64_val {:optional true} [:vector :sint64]]
          [:bool_val {:optional true} [:vector :boolean]]
          [:enum_val {:optional true} [:vector [:ref :my.ns.enum/Enum]]]
          [:fixed64_val {:optional true} [:vector :fixed64]]
          [:sfixed64_val {:optional true} [:vector :sfixed64]]
          [:double_val {:optional true} [:vector :double]]
          [:string_val {:optional true} [:vector :string]]
          [:bytes_val {:optional true} [:vector :bytes]]
          [:fixed32_val {:optional true} [:vector :fixed32]]
          [:sfixed32_val {:optional true} [:vector :sfixed32]]
          [:float_val {:optional true} [:vector :double]] ; TODO
          [:singular_msg {:optional true} [:vector [:ref :my.ns.singular/Singular]]]]))
  (is (true?  (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0 1 2 3]} registry)))
  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0.01]} registry)))
  #?(:clj  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0.0]} registry)))) ; for clj, 0.0 stays as double
  #?(:cljs (is (true?  (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0.0]} registry)))) ; for cljs, 0.0 becomes int 0
  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:a :b} registry))))

(deftest test-schema-malli-required
  (is (= (malli-schema :my.ns.required/Required)
         [:map
          {:closed true}
          [:int32_val :int32]
          [:int64_val :int64]
          [:uint32_val :uint32]
          [:uint64_val :uint64]
          [:sint32_val :sint32]
          [:sint64_val :sint64]
          [:bool_val :boolean]
          [:enum_val [:ref :my.ns.enum/Enum]]
          [:fixed64_val :fixed64]
          [:sfixed64_val :sfixed64]
          [:double_val :double]
          [:string_val :string]
          [:fixed32_val :fixed32]
          [:sfixed32_val :sfixed32]
          [:float_val :double]]))
  (is (true?  (m/validate [:ref :my.ns.required/Required] {:int32_val 0
                                                           :int64_val 0
                                                           :uint32_val 0
                                                           :uint64_val 0
                                                           :sint32_val 0
                                                           :sint64_val 0
                                                           :bool_val true
                                                           :enum_val :ZERO
                                                           :fixed64_val 0
                                                           :sfixed64_val 0
                                                           :double_val 0.0
                                                           :string_val ""
                                                           :fixed32_val 0
                                                           :sfixed32_val 0
                                                           :float_val 0.0}
                          registry)))
  (is (false? (m/validate [:ref :my.ns.required/Required] {:int32_val 0} registry)))
  (is (false? (m/validate [:ref :my.ns.required/Required] {:string_val ""} registry))))

(deftest test-schema-malli-implicit
  (is (= (malli-schema :my.ns.implicit/Implicit) [:map
                                                  {:closed true}
                                                  [:int32_val {} :int32]
                                                  [:string_val {} :string]]))
  (is (false? (m/validate [:ref :my.ns.implicit/Implicit] {} registry)))
  (is (false? (m/validate [:ref :my.ns.implicit/Implicit] {:int32_val 0} registry)))
  (is (false? (m/validate [:ref :my.ns.implicit/Implicit] {:string_val ""} registry)))
  (is (true? (m/validate [:ref :my.ns.implicit/Implicit] {:int32_val 0
                                                          :string_val ""} registry))))

(deftest test-schema-malli-singular
  (is (= (malli-schema :my.ns.singular/Singular)
         [:map
          {:closed true}
          [:int32_val {:optional true} :int32]
          [:int64_val {:optional true} :int64]
          [:uint32_val {:optional true} :uint32]
          [:uint64_val {:optional true} :uint64]
          [:sint32_val {:optional true} :sint32]
          [:sint64_val {:optional true} :sint64]
          [:bool_val {:optional true} :boolean]
          [:enum_val {:optional true} [:ref :my.ns.enum/Enum]]
          [:fixed64_val {:optional true} :fixed64]
          [:sfixed64_val {:optional true} :sfixed64]
          [:double_val {:optional true} :double]
          [:string_val {:optional true} :string]
          [:bytes_val {:optional true} :bytes]
          [:fixed32_val {:optional true} :fixed32]
          [:sfixed32_val {:optional true} :sfixed32]
          [:float_val {:optional true} :double]])) ; TODO
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:int32_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:int64_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:uint32_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:uint64_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sint32_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sint64_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:bool_val true} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:enum_val :ZERO} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:fixed64_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sfixed64_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:double_val 0.0} registry)))
  #?(:clj  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:bytes_val (byte-array 1)} registry))))
  #?(:cljs (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:bytes_val (js/Uint8Array. [1 2 3])} registry)))) 
  (is (false? (m/validate [:ref :my.ns.singular/Singular] {:bytes_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:fixed32_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sfixed32_val 0} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:float_val 0.0} registry)))

  #?(:clj  (is (false? (m/validate [:ref :my.ns.singular/Singular] {:int32_val 0.0} registry)))) ; for clj, 0.0 stays as double
  #?(:cljs (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:int32_val 0.0} registry)))) ; for cljs, 0.0 becomes int 0
  (is (false? (m/validate [:ref :my.ns.singular/Singular] {:a :b} registry))))

(deftest test-schema-malli-singular-boundary
  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:int32_val sint32-max} registry)))
  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:int32_val sint32-min} registry)))

  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:int64_val sint64-max} registry)))
  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:int64_val sint64-min} registry)))

  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:uint32_val uint32-min} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:uint32_val uint32-max} registry)))

  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:uint64_val uint64-min} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:uint64_val uint64-max} registry)))

  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sint32_val sint32-max} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sint32_val sint32-min} registry)))

  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:sint64_val sint64-max} registry)))
  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:sint64_val sint64-min} registry)))

  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:fixed64_val uint64-min} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:fixed64_val uint64-max} registry)))

  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:sfixed64_val sint64-max} registry)))
  (is (true? (m/validate [:ref :my.ns.singular/Singular] {:sfixed64_val sint64-min} registry)))

  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:fixed32_val uint32-min} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:fixed32_val uint32-max} registry)))

  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sfixed32_val sint32-max} registry)))
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:sfixed32_val sint32-min} registry))))

(deftest test-schema-malli-singular-out-of-range
  (is (false? (m/validate [:ref :my.ns.singular/Singular] {:int32_val (+ sint32-max 1)} registry)))
  (is (false? (m/validate [:ref :my.ns.singular/Singular] {:int32_val (- sint32-min 1)} registry)))

  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:uint32_val (- uint32-min 1)} registry)))
  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:uint32_val (+ uint32-max 1)} registry)))

  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:uint64_val (- uint64-min 1)} registry)))
  ; (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:uint64_val (+ uint64-max 1)} registry)))

  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:sint32_val (+ sint32-max 1)} registry)))
  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:sint32_val (- sint32-min 1)} registry)))

  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:fixed64_val (- uint64-min 1)} registry)))
  ; (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:fixed64_val (+ uint64-max 1)} registry)))

  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:fixed32_val (- uint32-min 1)} registry)))
  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:fixed32_val (+ uint32-max 1)} registry)))

  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:sfixed32_val (+ sint32-max 1)} registry)))
  (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:sfixed32_val (- sint32-min 1)} registry))))

#?(:clj
   (deftest test-schema-malli-singular-bigint-out-of-range
     (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:uint64_val (+ uint64-max 1)} registry)))

     (is (false?  (m/validate [:ref :my.ns.singular/Singular] {:fixed64_val (+ uint64-max 1)} registry)))

     (is (false? (m/validate [:ref :my.ns.singular/Singular] {:int64_val (+ sint64-max 1N)} registry)))
     (is (false? (m/validate [:ref :my.ns.singular/Singular] {:int64_val (- sint64-min 1N)} registry)))

     (is (false? (m/validate [:ref :my.ns.singular/Singular] {:sint64_val (+ sint64-max 1N)} registry)))
     (is (false? (m/validate [:ref :my.ns.singular/Singular] {:sint64_val (- sint64-min 1N)} registry)))

     (is (false? (m/validate [:ref :my.ns.singular/Singular] {:sfixed64_val (+ sint64-max 1N)} registry)))
     (is (false? (m/validate [:ref :my.ns.singular/Singular] {:sfixed64_val (- sint64-min 1N)} registry)))))


(deftest test-no-package
  (is (= (malli-schema :./Msg1)           [:map {:closed true} [:int32_val {:optional true} :int32]]))
  (is (= (malli-schema :./MsgA)           [:map {:closed true} [:int32_val {:optional true} :int32]]))
  (is (= (malli-schema :./MsgA.MsgB)      [:map {:closed true} [:int64_val {:optional true} :int64]]))
  (is (= (malli-schema :./MsgA.MsgB.MsgC) [:map {:closed true} [:uint32_val {:optional true} :uint32]])))

(deftest test-extension
  (is (= (malli-schema :my.ns.extension/Extendable) [:map
                                                     {:closed true}
                                                     [:int32_val {:optional true} :int32]
                                                     [:int64_val {:optional true} :int64]
                                                     [:my.ns.extension/Msg1.double_val {:optional true} :double]
                                                     [:my.ns.extension/string_val {:optional true} :string]])))
