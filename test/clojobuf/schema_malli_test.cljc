(ns clojobuf.schema-malli-test
  (:require #?(:clj [clojobuf.core :refer [protoc ->malli-registry]])
            #?(:cljs [clojobuf.nodejs :refer [protoc]])
            #?(:cljs [clojobuf.core :refer [->malli-registry]])
            [clojobuf.schema :refer [vschemas-update-msg-field-presence vschemas-make-defaults]]
            [clojobuf.constant :refer [sint32-max sint32-min sint53-max sint53-min sint64-max sint64-min uint32-max uint32-min uint64-max uint64-min]]
            [clojure.test :refer [is deftest run-tests]]
            [malli.core :as m]
            [malli.transform :as mt]))

(def codec_malli (protoc ["resources/protobuf/"] ["nested.proto",
                                                  "no_package.proto",
                                                  "extension.proto",
                                                  "required.proto", 
                                                  "implicit.proto"
                                                  "edition.proto"]
                         :auto-malli-registry false))
(def malli-schema (second codec_malli))
(def registry (->malli-registry malli-schema))

(deftest test-schema-:?
  (is (true?  (m/validate [:ref :my.ns.singular/Singular] {:? [[1 2 3]]} registry))))

(deftest test-schema-malli-edition
  (is (= (malli-schema :my.ns.edition/Edition)
         [:map
          {:closed true}
          [:? {:optional true, :presence :?, :default nil} :any]
          [:f1 {:optional true, :presence :optional} :int32]
          [:f2 {:optional true, :presence :optional, :default 42} :int32]
          [:f3 {:optional true, :presence :implicit} :int32]
          [:f4 {:presence :required} :int32]])))

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
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:uint32_sint64 {:optional true :presence :map} [:maybe [:map-of :uint32 :sint64]]]
          [:int64_string {:optional true :presence :map} [:maybe [:map-of :int64 :string]]]
          [:fixed32_double {:optional true :presence :map} [:maybe [:map-of :fixed32 :double]]]
          [:sfixed64_enum {:optional true :presence :map} [:maybe [:map-of :sfixed64 [:ref :my.ns.enum/Enum]]]]
          [:sint64_singular {:optional true :presence :map} [:maybe [:map-of :sint64 [:ref :my.ns.singular/Singular]]]]
          [:uint64_packed {:optional true :presence :map} [:maybe [:map-of :uint64 [:ref :my.ns.packed/Packed]]]]]))
  (is (true?  (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {0 0, 1 -1, 2 2, 3 -3}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {1 1.234}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:uint32_sint64 {1.234 0}} registry)))
  (is (false? (m/validate [:ref :my.ns.map/Mappy] {:a :b} registry))))

(deftest test-schema-malli-nested
  (is (= (malli-schema :my.ns.nested/Msg1)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:enum {:optional true :presence :implicit} [:ref :my.ns.enum/Enum]]
          [:nested2 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2]]
          [:nested3 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3]]
          [:nested4 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]
          [:nested5a {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]
          [:nested5b {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]
          [:nested5c {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:singular {:optional true :presence :optional} [:ref :my.ns.singular/Singular]]
          [:nested3 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3]]
          [:nested4 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:packed {:optional true :presence :optional} [:ref :my.ns.packed/Packed]]
          [:repeat {:optional true :presence :optional} [:ref :my.ns.repeat/Repeat]]
          [:nested4 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4]]
          [:nested5 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:mappy {:optional true :presence :optional} [:ref :my.ns.map/Mappy]]
          [:nested5 {:optional true :presence :optional} [:ref :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5]]]))
  (is (= (malli-schema :my.ns.nested/Msg1.Msg2.Msg3.Msg4.Msg5)
         [:map {:closed true} [:? {:optional true :presence :? :default nil} :any] [:either {:optional true, :presence :optional} [:ref :my.ns.oneof/Either]]])))

; TODO compare dynamic function at end of form (currently bypassed with drop-last)
(deftest test-schema-malli-oneof
  (is (= (malli-schema :my.ns.oneof/Either)
         [:and
          [:map
           {:closed true} [:? {:optional true :presence :? :default nil} :any]
           [:either
            {:optional true :presence :oneof}
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
           [:int32_val {:optional true :presence :oneof-field} [:maybe :int32]]
           [:int64_val {:optional true :presence :oneof-field} [:maybe :int64]]
           [:uint32_val {:optional true :presence :oneof-field} [:maybe :uint32]]
           [:uint64_val {:optional true :presence :oneof-field} [:maybe :uint64]]
           [:sint32_val {:optional true :presence :oneof-field} [:maybe :sint32]]
           [:sint64_val {:optional true :presence :oneof-field} [:maybe :sint64]]
           [:bool_val {:optional true :presence :oneof-field} [:maybe :boolean]]
           [:enum_val {:optional true :presence :oneof-field} [:maybe [:ref :my.ns.enum/Enum]]]
           [:fixed64_val {:optional true :presence :oneof-field} [:maybe :fixed64]]
           [:sfixed64_val {:optional true :presence :oneof-field} [:maybe :sfixed64]]
           [:double_val {:optional true :presence :oneof-field} [:maybe :double]]
           [:string_val {:optional true :presence :oneof-field} [:maybe :string]]
           [:bytes_val {:optional true :presence :oneof-field} [:maybe :bytes]]
           [:fixed32_val {:optional true :presence :oneof-field} [:maybe :fixed32]]
           [:sfixed32_val {:optional true :presence :oneof-field} [:maybe :sfixed32]]
           [:float_val {:optional true :presence :oneof-field} [:maybe :double]]
           [:singular_msg {:optional true :presence :oneof-field} [:maybe [:ref :my.ns.singular/Singular]]]
           [:packed_msg {:optional true :presence :oneof-field} [:maybe [:ref :my.ns.packed/Packed]]]
           [:msg_1 {:optional true :presence :oneof-field} [:maybe [:ref :./Msg1]]]
           [:msg_a {:optional true :presence :oneof-field} [:maybe [:ref :./MsgA]]]]
          [:oneof
           :either
           [:int32_val
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
            :msg_a]]]))
  (is (true?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val, :string_val "abc"} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:string_val "abc"} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:either :string_val, :uint32_val 1} registry)))
  (is (false?  (m/validate [:ref :my.ns.oneof/Either] {:a :b} registry))))

(deftest test-schema-malli-packed
  (is (= (malli-schema :my.ns.packed/Packed)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:int32_val {:optional true :presence :repeated} [:vector :int32]]
          [:int64_val {:optional true :presence :repeated} [:vector :int64]]
          [:uint32_val {:optional true :presence :repeated} [:vector :uint32]]
          [:uint64_val {:optional true :presence :repeated} [:vector :uint64]]
          [:sint32_val {:optional true :presence :repeated} [:vector :sint32]]
          [:sint64_val {:optional true :presence :repeated} [:vector :sint64]]
          [:bool_val {:optional true :presence :repeated} [:vector :boolean]]
          [:enum_val {:optional true :presence :repeated} [:vector [:ref :my.ns.enum/Enum]]]
          [:fixed64_val {:optional true :presence :repeated} [:vector :fixed64]]
          [:sfixed64_val {:optional true :presence :repeated} [:vector :sfixed64]]
          [:double_val {:optional true :presence :repeated} [:vector :double]]
          [:string_val {:optional true :presence :repeated} [:vector :string]]
          [:bytes_val {:optional true :presence :repeated} [:vector :bytes]]
          [:fixed32_val {:optional true :presence :repeated} [:vector :fixed32]]
          [:sfixed32_val {:optional true :presence :repeated} [:vector :sfixed32]]
          [:float_val {:optional true :presence :repeated} [:vector :double]] ; TODO
          [:singular_msg {:optional true :presence :repeated} [:vector [:ref :my.ns.singular/Singular]]]]))
  (is (true?  (m/validate [:ref :my.ns.packed/Packed] {:int32_val [0 1 2 3]} registry)))
  (is (false? (m/validate [:ref :my.ns.packed/Packed] {:a :b} registry))))

(deftest test-schema-malli-repeat
  (is (= (malli-schema :my.ns.repeat/Repeat)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:int32_val {:optional true :presence :repeated} [:vector :int32]]
          [:int64_val {:optional true :presence :repeated} [:vector :int64]]
          [:uint32_val {:optional true :presence :repeated} [:vector :uint32]]
          [:uint64_val {:optional true :presence :repeated} [:vector :uint64]]
          [:sint32_val {:optional true :presence :repeated} [:vector :sint32]]
          [:sint64_val {:optional true :presence :repeated} [:vector :sint64]]
          [:bool_val {:optional true :presence :repeated} [:vector :boolean]]
          [:enum_val {:optional true :presence :repeated} [:vector [:ref :my.ns.enum/Enum]]]
          [:fixed64_val {:optional true :presence :repeated} [:vector :fixed64]]
          [:sfixed64_val {:optional true :presence :repeated} [:vector :sfixed64]]
          [:double_val {:optional true :presence :repeated} [:vector :double]]
          [:string_val {:optional true :presence :repeated} [:vector :string]]
          [:bytes_val {:optional true :presence :repeated} [:vector :bytes]]
          [:fixed32_val {:optional true :presence :repeated} [:vector :fixed32]]
          [:sfixed32_val {:optional true :presence :repeated} [:vector :sfixed32]]
          [:float_val {:optional true :presence :repeated} [:vector :double]] ; TODO
          [:singular_msg {:optional true :presence :repeated} [:vector [:ref :my.ns.singular/Singular]]]]))
  (is (true?  (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0 1 2 3]} registry)))
  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0.01]} registry)))
  #?(:clj  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0.0]} registry)))) ; for clj, 0.0 stays as double
  #?(:cljs (is (true?  (m/validate [:ref :my.ns.repeat/Repeat] {:int32_val [0.0]} registry)))) ; for cljs, 0.0 becomes int 0
  (is (false? (m/validate [:ref :my.ns.repeat/Repeat] {:a :b} registry))))

(deftest test-schema-malli-required
  (is (= (malli-schema :my.ns.required/Required)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:int32_val {:presence :required} :int32]
          [:int64_val {:presence :required} :int64]
          [:uint32_val {:presence :required} :uint32]
          [:uint64_val {:presence :required} :uint64]
          [:sint32_val {:presence :required} :sint32]
          [:sint64_val {:presence :required} :sint64]
          [:bool_val {:presence :required} :boolean]
          [:enum_val {:presence :required} [:ref :my.ns.enum/Enum]]
          [:fixed64_val {:presence :required} :fixed64]
          [:sfixed64_val {:presence :required} :sfixed64]
          [:double_val {:presence :required} :double]
          [:string_val {:presence :required} :string]
          [:fixed32_val {:presence :required} :fixed32]
          [:sfixed32_val {:presence :required} :sfixed32]
          [:float_val {:presence :required} :double]]))
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
                                                  {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                                  [:int32_val {:optional true
                                                               :presence :implicit} :int32]
                                                  [:string_val {:optional true
                                                                :presence :implicit} :string]]))
  (is (true? (m/validate [:ref :my.ns.implicit/Implicit] {} registry)))
  (is (true? (m/validate [:ref :my.ns.implicit/Implicit] {:int32_val 0} registry)))
  (is (true? (m/validate [:ref :my.ns.implicit/Implicit] {:string_val ""} registry)))
  (is (true? (m/validate [:ref :my.ns.implicit/Implicit] {:int32_val 0
                                                          :string_val ""} registry))))

(deftest test-schema-malli-singular
  (is (= (malli-schema :my.ns.singular/Singular)
         [:map
          {:closed true} [:? {:optional true :presence :? :default nil} :any]
          [:int32_val {:optional true :presence :optional} [:maybe :int32]]
          [:int64_val {:optional true :presence :optional} [:maybe :int64]]
          [:uint32_val {:optional true :presence :optional} [:maybe :uint32]]
          [:uint64_val {:optional true :presence :optional} [:maybe :uint64]]
          [:sint32_val {:optional true :presence :optional} [:maybe :sint32]]
          [:sint64_val {:optional true :presence :optional} [:maybe :sint64]]
          [:bool_val {:optional true :presence :optional} [:maybe :boolean]]
          [:enum_val {:optional true :presence :optional} [:maybe [:ref :my.ns.enum/Enum]]]
          [:fixed64_val {:optional true :presence :optional} [:maybe :fixed64]]
          [:sfixed64_val {:optional true :presence :optional} [:maybe :sfixed64]]
          [:double_val {:optional true :presence :optional} [:maybe :double]]
          [:string_val {:optional true :presence :optional} [:maybe :string]]
          [:bytes_val {:optional true :presence :optional} [:maybe :bytes]]
          [:fixed32_val {:optional true :presence :optional} [:maybe :fixed32]]
          [:sfixed32_val {:optional true :presence :optional} [:maybe :sfixed32]]
          [:float_val {:optional true :presence :optional} [:maybe :double]]])) ; TODO
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
  (is (= (malli-schema :./Msg1)           [:map {:closed true} [:? {:optional true :presence :? :default nil} :any] [:int32_val {:optional true :presence :optional} [:maybe :int32]]]))
  (is (= (malli-schema :./MsgA)           [:map {:closed true} [:? {:optional true :presence :? :default nil} :any] [:int32_val {:optional true :presence :optional} [:maybe :int32]]]))
  (is (= (malli-schema :./MsgA.MsgB)      [:map {:closed true} [:? {:optional true :presence :? :default nil} :any] [:int64_val {:optional true :presence :optional} [:maybe :int64]]]))
  (is (= (malli-schema :./MsgA.MsgB.MsgC) [:map {:closed true} [:? {:optional true :presence :? :default nil} :any] [:uint32_val {:optional true :presence :optional} [:maybe :uint32]]])))

(deftest test-extension
  (is (= (malli-schema :my.ns.extension/Extendable) [:map
                                                     {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                                     [:int32_val {:optional true :presence :optional} [:maybe :int32]]
                                                     [:int64_val {:optional true :presence :optional} [:maybe :int64]]
                                                     [:my.ns.extension/Msg1.double_val {:optional true :presence :optional} [:maybe :double]]
                                                     [:my.ns.extension/string_val {:optional true :presence :optional} [:maybe :string]]])))

(deftest test-update-msg-field-presence
  (let [base {:my.ns/Enum [:enum :ZERO :ONE]
              :my.ns/MsgB [:map
                           {:closed true} [:? {:optional true :presence :? :default nil} :any]
                           [:field :int32]]}]
    (is (= (vschemas-update-msg-field-presence
            (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                      [:msg_val {:optional true :presence :implicit} [:ref :my.ns/MsgB]] ; :implicit
                                      [:enum_val {:optional true :presence :implicit} [:ref :my.ns/Enum]]]}))
           (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                     [:msg_val {:optional true :presence :optional} [:ref :my.ns/MsgB]] ; becomes :optional
                                     [:enum_val {:optional true :presence :implicit} [:ref :my.ns/Enum]]]})))
    
    (is (= (vschemas-update-msg-field-presence
            (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                      [:msg_val {:optional true :presence :optional}[:ref :my.ns/MsgB]] ; opitonal
                                      [:enum_val {:optional true :presence :optional} [:ref :my.ns/Enum]]]}))
           (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                     [:msg_val {:optional true :presence :optional} [:ref :my.ns/MsgB]]  ; no change
                                     [:enum_val {:optional true :presence :optional} [:ref :my.ns/Enum]]]}))) 
    
    (is (= (vschemas-update-msg-field-presence
            (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                      [:msg_val {:optional false :presence :required} [:ref :my.ns/MsgB]] ; required
                                      [:enum_val {:optional false :presence :required} [:ref :my.ns/Enum]]]}))
           (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                     [:msg_val {:optional false :presence :required} [:ref :my.ns/MsgB]]  ; no change
                                     [:enum_val {:optional false :presence :required} [:ref :my.ns/Enum]]]})))
    
    (is (= (vschemas-update-msg-field-presence
            (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                      [:msg_val {} [:ref :my.ns/MsgB]]                ; empty property
                                      [:enum_val {} [:ref :my.ns/Enum]]]}))
           (merge base {:my.ns/MsgA [:map {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                     [:msg_val {} [:ref :my.ns/MsgB]]                 ; no change
                                     [:enum_val {} [:ref :my.ns/Enum]]]})))))

(def test-vschemas {:my.ns/Enum [:enum :ZERO :ONE]
                    :my.ns/MsgA [:and
                                 [:map
                                  {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                  [:either
                                   {:optional true :presence :oneof}
                                   [:enum
                                    :int32_val
                                    :int64_val]]
                                  [:int32_val {:optional true :presence :oneof-field} :int32]
                                  [:int64_val {:optional true :presence :oneof-field} :int64]
                                  [:msg_val {:optional true :presence :implicit} [:ref :my.ns/MsgB]] ; implicit
                                  [:enum_val {:optional true :presence :implicit} [:ref :my.ns/Enum]]]
                                 [:oneof
                                  :either
                                  [:int32_val
                                   :int64_val]]]
                    :my.ns/MsgB [:map
                                 {:closed true} [:? {:optional true :presence :? :default nil} :any]
                                 [:field {:optional true :presence :implicit} :int32]]})

(deftest test-update-msg-with-oneof-field-presence
  (is (= (vschemas-update-msg-field-presence test-vschemas)
         {:my.ns/Enum [:enum :ZERO :ONE]
          :my.ns/MsgA [:and
                       [:map
                        {:closed true} [:? {:optional true :presence :? :default nil} :any]
                        [:either
                         {:optional true :presence :oneof}
                         [:enum
                          :int32_val
                          :int64_val]]
                        [:int32_val {:optional true :presence :oneof-field} :int32]
                        [:int64_val {:optional true :presence :oneof-field} :int64]
                        [:msg_val {:optional true :presence :optional} [:ref :my.ns/MsgB]] ; becomes: optional
                        [:enum_val {:optional true :presence :implicit} [:ref :my.ns/Enum]]]
                       [:oneof
                        :either
                        [:int32_val
                         :int64_val]]]
          :my.ns/MsgB [:map
                       {:closed true} [:? {:optional true :presence :? :default nil} :any]
                       [:field {:optional true :presence :implicit} :int32]]})))

(deftest test-vschemas-make-implicit-defaults
  (is (= (vschemas-make-defaults test-vschemas)
         {:my.ns/MsgA {:either nil, :int32_val nil, :int64_val nil, :msg_val nil, :enum_val :ZERO},
          :my.ns/MsgB {:field 0}})))

