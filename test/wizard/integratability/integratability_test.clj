(ns wizard.integratability.integratability-test
  (:use wizard.toolbelt.test.midje wizard.toolbelt)
  (:require [clojure.string :as str]
            [wizard.integratability :as intg]
            [wizard.contextual-resolution :as ctx]))

(facts
  "about counter integrations"
  (let [counter
        {:n 1000
         :available-integrations
         {'inc (fn [x {y :y} counter {{z :z} :data}]
                 (update counter :n + x y z))}
         :integrations
         [{:type :inc :data {:z 100}}
          (fn [_ _ x] (update x :n + 10000))
          {:type :inc :data {:z 200}}]}]
    (:n (intg/resolve-integrations-on counter 10 {:y 1}))
    => 11322))

(defn- append-inc
  [counter]
  (let [intg (intg/find-intg counter :inc)]
    (if (>= (-> intg :data :times) 100)
      counter
      (-> counter
          (intg/update-intg :inc #(update-in % [:data :times] + 1))
          (intg/prepend-intg append-inc)))))

(facts
  "about updating integration"
  (let [counter
        {:n 50
         :available-integrations
         {'inc (fn [counter {{:keys [times]} :data}]
                 (update counter :n + times))}
         :integrations
         [append-inc
          {:type :inc :data {:times 0}}]}]
    (:n (intg/resolve-integrations-on counter)) => 150))

(fact
 "incremental integration"
 (let [counter
       {:n 15
        :available-integrations
        {'inc-n
         (fn [counter {{:keys [up-to]} :data :as inc-n}]
           (if-not (< (:n counter) up-to)
             counter
             (-> counter
                 (update :n inc)
                 (update :integrations append inc-n))))}
        :integrations
        [{:type :inc-n
          :data {:up-to 50}}]}]
   (:n (intg/resolve-integrations-on counter)) => 50))

(fact
 "resolving integrated results in context"
 (let [ctx {:y 3 :z 5
            :thing
            {:x 2
             :available-integrations
             {'do
              (fn [y thing {}]
                (ctx/exfer :z #(update thing :x * y %)))}
             :integrations
             [{:type :do}
              (fn [y thing]
                (ctx/exfer :z #(update thing :x * y %)))]}}]
   (:x (intg/resolve-integrations-in ctx :thing :y)) => 450))

(fact
  "integrating informativally throughout context"
  (let [ctx {:y 2
             :thing
             {:x 3
              :available-integrations
              {'do
               (fn [y thing {:keys [zz]}]
                 (ctx/inform
                  {:z 5 :zz zz :zzz 9}
                  :z
                  (fn [z]
                    (update thing :x * y z))))}
              :integrations
              [{:type :do :data {:zz 999}}
               (fn [y thing]
                 (ctx/inform
                  {:zz 9}
                  :zz :zzz
                  (fn [zz zzz]
                    (assoc thing :z (* zz zzz)))))]}}
        {:keys [ctx resolved]}
        (intg/resolve-integrations-throughout ctx :thing :y)]
    resolved => (contains {:x 30 :z 81})
    ctx => (just {:y 2 :z 5 :thing (contains {:x 30 :z 81}) :zz 9 :zzz 9})))
