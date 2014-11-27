(ns reagent-forms.datepicker
   (:require
   [clojure.string :as s]
   [reagent.core :as reagent :refer [atom]]
   [goog.i18n.DateTimeFormat :as dtf]
   ))

(def dates
  {:days ["Sunday" "Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday" "Sunday"]
   :days-short ["Sun" "Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"]
   :months ["January" "February" "March" "April" "May" "June" "July" "August" "September" "October" "November" "December"]
   :month-short ["Jan" "Feb" "Mar" "Apr" "May" "Jun" "Jul" "Aug" "Sep" "Oct" "Nov" "Dec"]})

(def date-format-map
  (let [f goog.i18n.DateTimeFormat.Format]
    {:full-date (.-FULL_DATE f)
     :full-datetime (.-FULL_DATETIME f)
     :full-time (.-FULL_TIME f)
     :long-date (.-LONG_DATE f)
     :long-datetime (.-LONG_DATETIME f)
     :long-time (.-LONG_TIME f)
     :medium-date (.-MEDIUM_DATE f)
     :medium-datetime (.-MEDIUM_DATETIME f)
     :medium-time (.-MEDIUM_TIME f)
     :short-date (.-SHORT_DATE f)
     :short-datetime (.-SHORT_DATETIME f)
     :short-time (.-SHORT_TIME f)}))

(defn format-date-goog
  "Formats date using either build-in date format passed as keyword or format string passed as string, based on the current locale. See http://docs.closure-library.googlecode.com/git/class_goog_i18n_DateTimeFormat.html for more info. Pattern specifications are as in Java, see http://docs.closure-library.googlecode.com/git/local_closure_goog_i18n_datetimeformat.js.source.html."
  [{:keys [year month day]} date-format]
  (.format (goog.i18n.DateTimeFormat.
            (or ((keyword date-format) date-format-map) date-format))
           (js/Date. year month day)))

(defn split-parts [fmt separator]
  (vec
    (map keyword
         (s/split fmt (if separator (re-pattern separator) #"W+")))))

(defn parse-format [fmt]
  (let [fmt (or fmt "mm/dd/yyyy")
        separator (re-find #"[.\/\-\s].*?" fmt)
        parts (split-parts fmt separator)]
    (when (or (empty? parts) (nil? separator))
      (throw (js/Error. "Invalid date format.")))
    {:separator separator :parts parts}))

(defn leap-year? [year]
  (or
   (and
     (= 0 (mod year 4))
     (not= 0 (mod year 100)))
   (= 0 (mod year 400))))

(defn days-in-month [year month]
  ([31 (if (leap-year? year) 29 28) 31 30 31 30 31 31 30 31 30 31] month))

(defn blank-date []
  (doto (js/Date.)
      (.setHours 0)
      (.setMinutes 0)
      (.setSeconds 0)
      (.setMilliseconds 0)))

(defn parse-date [date fmt]
  (let [parts (s/split date (re-pattern (:separator fmt)))
        date (blank-date)
        fmt-parts (count (:parts fmt))]
    (if (= (count (:parts fmt)) (count parts))
      (loop [year (.getFullYear date)
             month (.getMonth date)
             day (.getDate date)
             i 0]
        (if (not= i fmt-parts)
          (let [val (js/parseInt (parts i) 10)
                val (if (js/isNaN val) 1 val)
                part ((:parts fmt) i)]
            (cond
             (some #{part} [:d :dd]) (recur year month val (inc i))
             (some #{part} [:m :mm]) (recur year (dec val) day (inc i))
             (= part :yy)            (recur (+ 2000 val) month day (inc i))
             (= part :yyyy)          (recur val month day (inc i))))
          (js/Date. year month day 0 0 0)))
      date)))

(defn formatted-value [v]
  (str (if (< v 10) "0" "") v))

(defn format-date [{:keys [year month day]} {:keys [separator parts]}]
  (s/join separator
          (map
           #(cond
             (some #{%} [:d :dd]) (formatted-value day)
             (some #{%} [:m :mm]) (formatted-value month)
             (= % :yy)            (.substring (str year) 2)
             (= % :yyyy)          year)
           parts)))

(defn first-day-of-week [year month]
  (.getDay
   (doto (js/Date.)
     (.setYear year)
     (.setMonth month)
     (.setDate 1))))

(defn gen-days [[year month] get save! expanded?]
  (let [num-days (days-in-month year month)
        last-month-days (if (pos? month) (days-in-month year (dec month)))
        first-day (first-day-of-week year month)]
    (->>
     (for [i (range 42)]
       (cond
         (< i first-day)
         [:td.day.old
          (when last-month-days
           (- last-month-days (dec (- first-day i))))]
         (< i (+ first-day num-days))
         (let [day (inc (- i first-day))
               date {:year year :month month :day day}]
           [:td.day
             {:class (when-let [doc-date (get)]
                      (when (= doc-date date) "active"))
             :on-click #(if (= (get) date)
                          (save! nil)
                          (do 
                          (save! date)
                          (swap! expanded? not)
                          ))}
            day])
         :else
         [:td.day.new
          (when (< month 11)
           (inc (- i (+ first-day num-days))))]))
     (partition 7)
     (map (fn [week] (into [:tr] week))))))

(defn last-date [[year month]]
  (if (pos? month)
    [year (dec month)]
    [(dec year) 11]))

(defn next-date [[year month]]
  (if (= month 11)
    [(inc year) 1]
    [year (inc month)]))

;;TODO handle month, year views
(defn datepicker [year month expanded? get save!]
  (let [date (atom [year month])]
    (fn []
      [:div
       {:class (str "datepicker"(when-not @expanded? " dropdown-menu"))}
       [:table.table-condensed
        [:thead
         [:tr
           [:th.prev {:on-click #(swap! date last-date)} "‹"]
           [:th.switch
            {:col-span 5}
            (str (get-in dates [:months (second @date)]) " " (first @date))]
           [:th.next {:on-click #(swap! date next-date)} "›"]]
         [:tr
           [:th.dow "Su"]
           [:th.dow "Mo"]
           [:th.dow "Tu"]
           [:th.dow "We"]
           [:th.dow "Th"]
           [:th.dow "Fr"]
           [:th.dow "Sa"]]]
        (into [:tbody] (gen-days @date get save! expanded?))]])))
