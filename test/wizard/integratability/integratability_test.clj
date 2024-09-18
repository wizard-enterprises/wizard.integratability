(ns wizard.integratability.integratability-test
  (:use wizard.toolbelt.test wizard.toolbelt)
  (:require [wizard.toolbelt.test.matchers :as m]
            [wizard
             [integratability :as intg]
             [contextually :as ctx]]
            [clojure.string :as str]))

(deftest test-integratability
  (testing "adding integrations"
    (let [intg (fn [x] x)]
      (is (= {:integrations [intg]}
             (intg/append-intg {} intg)
             (intg/prepend-intg {} intg)))))

  (testing "counter integration incrementing itself recursively"
    (is (= 11322
           (:n (->
                {:n 1000
                 :available-integrations
                 {'inc (fn [x {y :y} counter {{z :z} :data}]
                         (update counter :n + x y z))}
                 :integrations
                 [{:type :inc :data {:z 100}}
                  (fn [_ _ x] (update x :n + 10000))
                  {:type :inc :data {:z 200}}]}
                (intg/resolve-integrations-on 10 {:y 1}))))))

  (testing "updating integrations"
    (letfn [(append-inc [counter]
              (let [intg (intg/find-intg counter :inc)]
                (if (>= (-> intg :data :times) 100)
                  counter
                  (-> counter
                      (intg/update-intg :inc #(update-in % [:data :times] + 1))
                      (intg/prepend-intg append-inc)))))]
      (is (= 150
             (:n (intg/resolve-integrations-on
                  {:n 50
                   :available-integrations
                   {'inc (fn [counter {{:keys [times]} :data}]
                           (update counter :n + times))}
                   :integrations
                   [append-inc
                    {:type :inc :data {:times 0}}]}))))))

  (testing "incremental integration"
    (is (= 50
           (:n (intg/resolve-integrations-on
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
                   :data {:up-to 50}}]})))))

  (testing "resolving integrated results in context"
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
      (is (= 450 (:x (intg/resolve-integrations-in ctx :thing :y))))))

  (testing "integrating informativally throughout context"
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
      (is (match? {:x 30 :z 81} resolved))
      (is (match? {:y 2 :z 5 :thing {:x 30 :z 81} :zz 9 :zzz 9} ctx)))))
