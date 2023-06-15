(ns cloakey.deployment
  (:import
    [org.keycloak.adapters
     KeycloakDeployment
     KeycloakDeploymentBuilder]
    [org.keycloak.adapters.rotation AdapterTokenVerifier]
    [org.keycloak.representations
     AccessToken
     AccessTokenResponse]))


(defn ^AdapterConfig adapter-config 
  [])


(defn ^KeycloakDeployment deployment
  [{:keys [^String realm ^String url ^String client]}]
  (KeycloakDeploymentBuilder/build
    ))
