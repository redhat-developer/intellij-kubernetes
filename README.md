# IntelliJ Kubernetes

## Overview

JetBrains IntelliJ plugin for interacting with Kubernetes and OpenShift clusters.
The plugin tries to mimic the vscode-kubernetes-tools extension that exists for vscode.
This plugin is currently in Preview Mode.

![](images/demo1.gif)

### Kubernetes & OpenShift resource tree
The plugin offers a tool window with a tree that displays all the resources that exist on a Kubernetes or OpenShift cluster.
The tree displays all the contexts that exist in your kube config (~/.kube/config).
The tree is dynamically updated when external tools like kubectl, oc, etc. change the kube config. 

#### Current Context
The tree displays resources that exist on the cluster that the current context points to. 
The user can switch the current context and cluster by choosing any context that's listed and picking "Set as Current Cluster" in the context menu.
OpenShift clusters are marked as such with a OpenShift icon instead of a Kubernetes icon.
 
#### Current Namespace/Project
The tree displays resources that exist within the current namespace as specified in the kube config. 
Resources that are not bound to a namespace/project are also listed.
The user may switch the current namespace/project by choosing a namespace/project among the listed ones and choose "Use Namespace" or "Use Project" in the context menu.

#### Resource Categories
Resources are organized in different categories.
![img.png](images/categories.png)

#### Pods
Pods are marked as running if their icon has a green dot. 
A red dot, on the contrary, indicates that a pod is either pending, succeeded (terminated), failed or whose state is unknown.
Pods also unveil their IP address and the number of running out of the total number of containers.
![img.png](images/pod.png)

#### Delete Resources
Any resource that's listed may be deleted via the context menu.


**NOTE:** This plugin is in Preview mode. The plugin support for Kubernetes or OpenShift clusters is strictly experimental - assumptions may break, commands and behavior may change!

## Release notes
See the change log.

Contributing
============
This is an open source project open to anyone. This project welcomes contributions and suggestions!

For information on getting started, refer to the [CONTRIBUTING instructions](CONTRIBUTING.md).

Feedback & Questions
====================
If you discover an issue please file a bug and we will fix it as soon as possible.
* File a bug in [GitHub Issues](https://github.com/redhat-developer/intellij-kubernetes/issues).

License
=======
EPL 2.0, See [LICENSE](LICENSE) for more information.
