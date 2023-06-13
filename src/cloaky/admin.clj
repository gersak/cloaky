(ns cloaky.admin
  (:require
    clojure.set
    [cloaky.core :as kc])
  (:import
    [org.keycloak.admin.client Keycloak]
    [org.keycloak.admin.client.resource ClientResource]
    [javax.ws.rs.core Response]
    [org.keycloak.representations.idm
     ClientRepresentation
     UserRepresentation
     GroupRepresentation
     RoleRepresentation
     RealmRepresentation
     CredentialRepresentation]
    [org.keycloak.admin.client.resource
     UsersResource
     UserResource
     GroupResource
     GroupsResource
     RealmResource]))



(defn check-response
  [^Response response]
  (let [status (.getStatus response)]
    (case status
      200 nil
      201 nil
      204 nil
      (throw
        (ex-info "Response failed"
                 {:status status
                  :message (.getStatusInfo response)})))))


(defonce ^:dynamic *client* nil)


; (extend RealmRepresentation
;   {:toString (fn [this]
;                (str "RealmRepresentation[" (.getRealm this) ", " (.getDisplayName this) "]"))})


(defn realms
  ([] (realms *client*))
  ([^Keycloak client]
   (reduce
     (fn [r realm]
       (assoc r
         (or (.getRealm realm))))
     nil
     (.findAll (.realms client)))))


(defn realm-representation
  (^RealmRepresentation
    [realm-name]
    (realm-representation realm-name realm-name))
  (^RealmRepresentation
    [realm-id realm-name]
    (realm-representation realm-id realm-name true))
  (^RealmRepresentation
    [^String realm-id ^String realm-name ^Boolean active?]
    (doto
      (RealmRepresentation.)
      (.setRealm realm-id)
      (.setDisplayName realm-name)
      (.setEnabled active?))))


(defn get-realm
  ([^String realm]
   (get-realm *client* realm))
  ([^Keycloak client ^String realm]
   (.realm (.realms client) realm)))


(defn create-realm
  ([^RealmRepresentation realm]
   (create-realm *client* realm))
  ([^Keycloak client ^RealmRepresentation realm]
   (.create (.realms client) realm)
   nil))


(defn delete-realm
  ([^String realm]
   (delete-realm *client* realm))
  ([^Keycloak client ^String realm]
   (.remove (.realm (.realms client) realm))))

;; Users

(defn realm-users
  (^UsersResource [^String realm]
   (.users (get-realm realm)))
  (^UsersResource [^Keycloak client ^String realm]
   (.users (get-realm client realm))))


(defn search-users
  ([^String realm] (search-users *client* realm))
  ([^Keycloak client ^String realm] (search-users client realm nil))
  ([^Keycloak client ^String realm {:keys [username
                                           first-name
                                           last-name
                                           email
                                           email-verified?
                                           enabled
                                           offset
                                           limit
                                           exact?]
                                    :as options}]
   (map
     kc/<-representation
     (if (nil? options)
       (.list (realm-users client realm))
       (.search (realm-users client realm)
                username
                first-name
                last-name
                email
                offset
                limit)))))


(declare build-group build-groups build-role build-roles)


(let [type-mapping {:password CredentialRepresentation/PASSWORD
                    :secret CredentialRepresentation/SECRET
                    :kerberos CredentialRepresentation/KERBEROS
                    :hotp CredentialRepresentation/HOTP
                    :totp CredentialRepresentation/TOTP}
      reverse-type-mapping (clojure.set/map-invert type-mapping)]
  (extend-type cloaky.core.Credential
    cloaky.core/Clojure2KeycloakRepresentation
    (->representation
      [{:keys [id
               priority
               value
               created-at
               credential-data
               secret-data
               temporary
               type
               user-label]}]
      (let [x (CredentialRepresentation.)]
        (when temporary (.setTemporary x temporary))
        (when created-at (.setCreatedDate x created-at))
        (when id (.setId x id))
        (when priority (.setPriority x priority))
        (when secret-data (.setSecretData x secret-data))
        (when credential-data (.setCredentialData x credential-data))
        (when type
          (.setType x (type-mapping type)))
        (when value (.setValue x value))
        ;; FIXME - this isn't working, at least it is working from
        ;; this side, but data that is set by using .setUserLabel
        ;; isn't perceived at keycloak side
        (when user-label (.setUserLabel x user-label))
        x)))


  (extend-type CredentialRepresentation
    cloaky.core/KeycloakRepresentation2Clojure
    (<-representation [this]
      (kc/map->Credential
        {:id (some-> (.getId this) (#(java.util.UUID/fromString %)))
         :priority (.getPriority this)
         :temporary (.isTemporary this)
         :value (.getValue this)
         :credential-data (.getCredentialData this)
         :secret-data (.getSecretData this)
         :type (reverse-type-mapping (.getType this)) 
         :user-label (.getUserLabel this)}))))



(extend-type cloaky.core.User
  cloaky.core/Clojure2KeycloakRepresentation
  (->representation
    [{:keys [email
             email-verified
             username
             first-name
             last-name
             credentials
             groups
             id
             realm-roles
             required-actions
             federation-identities
             federation-link
             enabled
             attributes
             created-at
             social-links]}]
    (let [_user (UserRepresentation.)]
      (.setUsername _user username)
      (when email (.setEmail _user email))
      (when first-name (.setFirstName _user first-name))
      (when enabled (.setEnabled _user enabled))
      (when (some? email-verified)) (.setEmailVerified _user email)
      (when last-name (.setLastName _user last-name))
      (when id (.setId _user (str id)))
      (when credentials (.setCredentials _user (map kc/->representation credentials)))
      (when (not-empty realm-roles) (.setRealmRoles _user (map kc/->representation realm-roles)))
      (when (not-empty required-actions) (.setRequiredActions _user required-actions))
      ;;
      (when federation-link (.setFederationLink _user (map kc/->representation federation-link)))
      (when (not-empty attributes) (.setAttributes _user attributes))
      (when (not-empty social-links) (.setSocialLinks _user (map kc/->representation social-links)))
      _user)))



(extend-type UserRepresentation
  cloaky.core/KeycloakRepresentation2Clojure
  (<-representation [this]
    (kc/map->User
      {:id (some-> (.getId this) (#(java.util.UUID/fromString %)))
       :username (.getUsername this)
       :first-name (.getFirstName this)
       :last-name (.getLastName this)
       :attributes (.getAttributes this)
       :email (.getEmail this)
       :email-verified (.isEmailVerified this)
       :group (.getGroups this)
       :realm-roles (.getRealmRoles this)
       :client-roles (.getClientRoles this)
       :required-actions (.getRequiredActions this)
       :federation-link (.getFederationLink this)
       :enabled (.isEnabled this)
       :credentials (map kc/<-representation (.getCredentials this))
       :federated-identities (map kc/<-representation (.getFederatedIdentities this))})))


(defn realm-user
  (^UserResource [^String realm id]
   (realm-user *client* realm (str id)))
  (^UsersResource [^Keycloak client ^String realm id]
   (let [users (realm-users client realm)]
     (.get users (str id)))))


(defn get-user-by-username
  ([^String realm ^String username]
   (get-user-by-username *client* realm username))
  ([^Keycloak client ^String realm ^String username]
   (let [users (realm-users client realm)
         [user] (.search users username true)]
     (kc/<-representation user))))


(defn get-user
  ([^String realm id]
   (get-user *client* realm id))
  ([^Keycloak client ^String realm id]
   (when-some [user (realm-user client realm id)]
     (kc/<-representation (.toRepresentation user)))))


(defn update-user
  ([^String realm user]
   (update-user *client* realm user))
  ([^Keycloak client ^String realm user]
   (when-let [_user (realm-user client realm (:id user))]
     (.update _user (kc/->representation user))
     (get-user client realm (:id user)))))


(defn create-user
  ([^String realm user]
   (create-user *client* realm user))
  ([^Keycloak client ^String realm user]
   (let [users (realm-users client realm)]
     (.create users (kc/->representation user))
     (get-user-by-username client realm (:username user)))))


(defn get-user-credentials
  ([^String realm user]
   (get-user-credentials *client* realm user))
  ([^Keycloak client ^String realm {:keys [id]}]
   (let [users (realm-users client realm)]
     (when-some [user (realm-user client realm (str id))]
       (map kc/<-representation (.credentials user))))))


(defn get-user-groups
  ([^String realm user]
   (get-user-groups *client* realm user))
  ([^Keycloak client ^String realm {:keys [id]}]
   (let [users (realm-users client realm)]
     (when-some [user (realm-user client realm (str id))]
       (map kc/<-representation (.groups user))))))

(defn delete-user
  ([^String realm user]
   (delete-user *client* realm user))
  ([^Keycloak client ^String realm {:keys [id]}]
   (when-some [users (realm-users client realm)]
     (check-response (.delete users (str id))))))




;; Groups

(defn realm-groups
  (^GroupsResource
    [^String realm]
    (.groups (get-realm realm)))
  (^GroupsResource
    [^Keycloak client ^String realm]
    (.groups (get-realm client realm))))



(extend-type cloaky.core.Group
  cloaky.core/Clojure2KeycloakRepresentation
  (->representation
    [{:keys [id
             name
             path
             realm-roles
             client-roles
             sub-groups
             access
             attributes]}]
    (let [x (GroupRepresentation.)]
      (.setName x name)
      (when id (.setId x (str id)))
      (when path (.setPath x path))
      (when (not-empty realm-roles) (.setRealmRoles x realm-roles))
      (when (not-empty client-roles) (.setClientRoles x client-roles))
      (when (not-empty sub-groups) (.setSubGroups x (map kc/->representation sub-groups)))
      (when (not-empty access) (.setAccess x access))
      (when (not-empty attributes) (.setAttributes x attributes))
      x)))



(extend-type GroupRepresentation
  cloaky.core/KeycloakRepresentation2Clojure
  (<-representation [this]
    (kc/map->Group
      {:id (some-> (.getId this) (#(java.util.UUID/fromString %)))
       :name (.getName this)
       :attributes (.getAttributes this)
       :access (.getAccess this)
       :path (.getPath this)
       :sub-groups (map kc/<-representation (.getSubGroups this))
       :realm-roles (map kc/<-representation (.getRealmRoles this))
       :client-roles (map kc/<-representation (.getClientRoles this))})))



(defn search-groups
  ([^String realm] (search-groups *client* realm))
  ([^Keycloak client ^String realm] (search-groups client realm nil))
  ([^Keycloak client ^String realm {:keys [search offset limit] :as options}]
   (let [groups (realm-groups client realm)]
     (map
       kc/<-representation
       (if (nil? options)
         (.groups groups)
         (.groups offset limit false))))))


(defn realm-group
  (^GroupResource
    [^String realm id]
    (realm-group *client* realm id))
  (^GroupResource
    [^Keycloak client ^String realm id]
    (let [groups (realm-groups client realm)]
      (.group groups (str id)))))


(defn get-group
  ([^String realm id]
   (get-user *client* realm id))
  ([^Keycloak client ^String realm id]
   (when-some [group (realm-group client realm id)]
     (kc/<-representation (.toRepresentation group)))))


(defn get-group-by-name
  ([^String realm ^String _name]
   (get-group-by-name *client* realm _name))
  ([^Keycloak client ^String realm ^String _name]
   (let [[group] (.groups (realm-groups client realm) _name true nil nil false)]
     (when group (kc/<-representation group)))))


(defn create-group
  ([^String realm group]
   (create-group *client* realm group))
  ([^Keycloak client ^String realm group]
   (let [groups (realm-groups client realm)]
     (check-response (.add groups (kc/->representation group)))
     ;; TODO - think about implementing this
     ;; .add groups doesn't create sub-groups from input Group record
     (get-group-by-name client realm (:name group))
     #_(let [{current-subgroups :sub-groups} (get-group-by-name client realm (:name group))
             {target-subgroups :sub-groups} group]
         ))))


(defn group-members
  ([^String realm group] (group-members *client* realm group))
  ([^Keycloak client ^String realm group] (group-members client realm group nil))
  ([^Keycloak client ^String realm group {:keys [limit offset]}]
   (map
     kc/<-representation
     (.members
       (realm-group client realm (:id group))
       offset limit))))


(defn delete-group
  ([^String realm group]
   (delete-group *client* realm group))
  ([^Keycloak client ^String realm {:keys [id]}]
   (when-some [group (realm-group client realm id)]
     (.remove group))))


;; ROLES



(comment
  (do
    (def client (Keycloak/getInstance "http://localhost:9090/" "master" "admin" "admin" "admin-cli"))
    (def realm "kbdev"))
  ;;
  ;;
  ;;
  (def id #uuid "e38c04ff-0dd5-495b-8ff1-bbd23a203f4d")
  (def _name "Test1")
  (def offset nil)
  (def limit nil)
  (map kc/<-representation (.groups (realm-groups client realm) "Test1" nil nil))
  (search-groups client realm)
  (delete-group client realm (-> *1 first))
  (delete-group client realm {:id #uuid "edffebf7-2773-4705-b7ea-3552b76aaba2"})
  (get-group client realm #uuid "e38c04ff-0dd5-495b-8ff1-bbd23a203f4d")
  (kc/<-representation (.toRepresentation (realm-group client realm id)))
  (.remove (realm-group client realm id))
  (get-group-by-name client realm "Test1")
  (def group-data
    (kc/map->Group
      {:name "Test1"
       :sub-groups
       (map
         kc/map->Group
         [{:name "test 2"
           :path "Test1/test 2"}
          {:name "test 3"
           :path "Test1/test 3"}])}))
  (def group-representation (kc/->representation group-data))
  (kc/<-representation group-representation)
  (.getId group-representation)
  ;; init
  (def group (create-group client realm group-data))
  (group-members client realm group)
  (check-response
    (.subGroup
      (realm-group client realm (:id group))
      (kc/->representation
        (kc/map->Group
          {:name "test3"}))))
  ;;
  (create-group client realm group)
  (delete-group client realm group)
  ;;
  (doseq [group [{:name "test 2"}
                 {:name "test 3"}]]
    (create-group client realm (kc/map->Group group))))
