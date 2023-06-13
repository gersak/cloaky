(ns neyho.eywa.keycloak)


(defprotocol Clojure2KeycloakRepresentation
  (->representation [this] "Casts current record to appropriate Keycloak Representation"))


(defprotocol KeycloakRepresentation2Clojure
  (<-representation [this] "Casts keycloak representation to appropirate clojure record"))


(defrecord User
  [email
   email-verified
   username
   first-name
   last-name
   groups
   id
   client-roles
   realm-roles
   required-actions
   federation-identities
   federation-link
   enabled
   attributes
   created-at
   social-links])


(defrecord Group
  [id
   name
   path
   realm-roles
   client-roles
   sub-groups
   access
   attributes])


(defrecord Role
  [])


(defrecord SocialLink
  [])


(defrecord FederatedIdentity
  [])


(defrecord Credential
  [id
   priority
   value
   created-at
   credential-data
   secret-data
   temporary
   type
   user-label])
