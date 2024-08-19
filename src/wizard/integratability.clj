(ns wizard.integratability
  (:use wizard.toolbelt))

(defn- find-available-integration
  [thing integration]
  (let [{type :type}           integration
        available-integrations (get thing :available-integrations {})]
    (get available-integrations (symbol type))))

(defn- resolve-integration-on
  [& args]
  (let [[thing integration] (take-last 2 args)
        ctx-args (drop-last 2 args)]
    (if (fn? integration)
      (apply integration (append ctx-args thing))
      (if-let [integrate (find-available-integration thing integration)]
        (apply integrate (append ctx-args thing integration))
        thing))))

(defn resolve-integrations
  [thing & args]
  (loop [thing thing]
    (if (empty? (:integrations thing))
      thing
      (let [integration (first (:integrations thing))
            thing       (-> thing
                            (update :integrations #(drop 1 %))
                            (update :integrated-integrations
                                    #(append (or % []) integration)))
            args        (append (or args []) thing integration)]
        (recur (apply resolve-integration-on args))))))

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
