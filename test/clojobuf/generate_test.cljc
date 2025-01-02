(ns clojobuf.generate-test
  (:require [clojobuf.core :refer [encode decode generate ->malli-registry]]
            [clojobuf.macro :refer [protoc-macro]]
            [clojure.test :refer [is deftest run-tests]]))


; use protoc-macro as it has more possibilites of failing than protoc
(def schemas (protoc-macro ["resources/protobuf/"] ["simple.proto"]))
(def registry (let [[codec malli] schemas] [codec (->malli-registry malli)]))

(defn codec [msg-id msg]
  (->> msg
       (encode registry msg-id)
       (decode registry msg-id)))

#?(:clj (defmacro rt [msg-id msg]
          `(is (= (codec ~msg-id ~msg) ~msg))))


; TODO figure out how to use macro for cljs so that error line number tallies with source
#?(:cljs (defn rt [msg-id msg]
           (is (= (codec msg-id msg) msg))))

(deftest test-generate
  (let [val (generate registry :my.ns.simple/Simple)
        val (dissoc val :?)]
    (rt :my.ns.simple/Simple val)))
