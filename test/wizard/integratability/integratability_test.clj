(ns wizard.integratability.integratability-test
  (:use wizard.toolbelt.test.midje wizard.toolbelt)
  (:require [clojure.string :as str]
            [wizard.integratability :as intg]))

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
    (:n (intg/resolve-integrations counter 10 {:y 1}))
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
    (:n (intg/resolve-integrations counter)) => 150))

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
   (:n (intg/resolve-integrations counter)) => 50))
