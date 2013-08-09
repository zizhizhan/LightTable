(ns lt.objs.thread
  (:require [lt.object :as object]
            [lt.objs.files :as files]
            [lt.objs.platform :as platform]
            [lt.objs.console :as console]
            [cljs.reader :as reader])
  (:require-macros [lt.macros :refer [thread]]))

(def cp (js/require "child_process"))

(object/behavior* ::try-send
                  :triggers #{:try-send!}
                  :reaction (fn [this msg]
                              (if-not (:connected @this)
                                (object/raise this :queue! msg)
                                (object/raise this :send! msg))))

(object/behavior* ::queue!
                  :triggers #{:queue!}
                  :reaction (fn [this msg]
                              (object/update! this [:queue] conj msg)))

(object/behavior* ::send!
                  :triggers #{:send!}
                  :reaction (fn [this msg]
                              (.send (:worker @this) (clj->js msg))))

(object/behavior* ::connect
                  :triggers #{:connect}
                  :reaction (fn [this]
                              (doseq [q (:queue @this)]
                                (object/raise this :send! q))
                              (object/merge! this {:connected true
                                                   :queue []})))

(object/behavior* ::message
                  :triggers #{:message}
                  :reaction (fn [this m]
                              (when-let [id (.-obj m)]
                                (object/raise (object/by-id id)
                                              (if-not (keyword? (.-msg m))
                                                (keyword (.-msg m))
                                                (.-msg m))
                                              (if (= "clj" (.-format m))
                                                (reader/read-string (.-res m))
                                                (.-res m))))))

(object/behavior* ::kill!
                  :triggers #{:kill!}
                  :reaction (fn [this]
                              (.kill (:worker @this))))

(defn node-exe []
  (if (platform/win?)
    "/plugins/node/node.exe"
    "/plugins/node/node"))

(object/object* ::worker-thread
                :tags #{:worker-thread}
                :queue []
                :init (fn [this]
                        (let [worker (.fork cp (files/lt-home "/core/node_modules/lighttable/background/threadworker.js") (clj->js ["--harmony"]) (clj->js {:execPath (files/lt-home (node-exe)) :silent false}))]
                          (.on worker "message" (fn [m] (object/raise this :message m)))
                          (.send worker (clj->js {:msg "init"
                                                  :obj (object/->id this)
                                                  :ltpath (files/lt-home)}))
                          (object/merge! this {:worker worker})
                        nil)))

;; TODO: move to default.behaviors
(object/tag-behaviors :worker-thread [::kill! ::connect ::send! ::queue! ::try-send ::message])

(defn send [msg]
  (object/raise worker :try-send! msg))

(defn thread* [func]
  (let [func-str (str "" func)
        n (gensym "theadfunc")] ;;trim off the errant return and outer function
    (send {:msg "register"
           :name n
           :func func-str})
    (fn [obj & args]
      (send {:msg "call"
             :name n
             :obj (object/->id obj)
             :params (map pr-str args)}))))

(def worker (object/create ::worker-thread))

(comment

(object/raise test :kill!)
(object/destroy! test)

(def t (thread (fn [m]
          (.log js/console "this is a message! " m)
          )))

(t test "blah")


  )