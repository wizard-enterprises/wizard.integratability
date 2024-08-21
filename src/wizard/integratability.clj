(ns wizard.integratability
  (:use wizard.toolbelt)
  (:require [wizard.contextual-resolution :as ctx]))

(defn append-intg
  [thing & intgs]
  (apply update thing :integrations append intgs))

(defn prepend-intg
  [thing & intgs]
  (update thing :integrations #(apply prepend (append intgs %))))

(defn find-intg
  [thing intg-type]
  (some #(when (= intg-type (:type %)) %)
        (:integrations thing)))

(defn update-intg
  [thing intg-type update-fn]
  (update
   thing :integrations
   #(update
     (into [] %)
     (index-of (find-intg thing intg-type) %)
     update-fn)))

(defn- find-available-integration
  [thing integration]
  (let [{type :type}           integration
        available-integrations (get thing :available-integrations {})]
    (get available-integrations (symbol type))))

(defn- resolve-integration-on
  [ctx thing integration & ctx-args]
  (if (fn? integration)
    (apply integration (append (or ctx-args []) thing))
    (if-let [integrate (find-available-integration thing integration)]
      (apply integrate (append (or ctx-args []) thing integration))
      thing)))

(defn- ctx-resolve
  [ctx thing]
  (ctx/resolve-in ctx (ctx/exfer thing identity)))

(defn resolve-integrations-throughout
  [ctx thing & ctx-args]
  (let [thing-path (if (vector? thing) thing [thing])]
    (loop [ctx   ctx
           thing (ctx-resolve ctx thing)]
      (if (empty? (:integrations thing))
        {:resolved thing :ctx (assoc-in ctx thing-path thing)}
        (let [integration (first (:integrations thing))
              thing       (-> thing
                              (update :integrations #(drop 1 %))
                              (update :integrated-integrations
                                      #(append (or % []) integration)))
              ctx-args    (map #(ctx-resolve ctx %) (or ctx-args []))
              integrated  (apply resolve-integration-on
                                 ctx thing integration ctx-args)
              {:keys [ctx resolved]}
              (ctx/resolve-throughout ctx integrated)]
          (recur ctx resolved))))))

(defn resolve-integrations-in
  [ctx thing & ctx-args]
  (:resolved (apply resolve-integrations-throughout ctx thing ctx-args)))

(defn resolve-integrations-on
  [thing & ctx-args]
  (let [ctx-args (reduce-kv assoc {} (into [] ctx-args))
        ctx      {::_ (assoc ctx-args :thing thing)}]
    (apply resolve-integrations-in
           ctx [::_ :thing]
           (map #(vector ::_ %) (keys ctx-args)))))
