 #!/usr/bin/env bash

# Note: run from root
# This is used to run and connect minikube cluster

pgrep -f "[m]inikube" >/dev/null || minikube start || { echo 'Cannot start minikube.'; exit 1; }
eval "$(minikube docker-env)" || { echo 'Cannot switch to minikube docker'; exit 1; }
kubectl config use-context minikube

# Check if cluster is in config, if not - here will be the empty labels
kubectl config view
