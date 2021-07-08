# Kubernetes

## Overview

JetBrains IDEA plugin for interacting with Kubernetes and OpenShift clusters.
The plugin provides functionalities and user experiences that are very close to the Kubernetes extension for vscode (https://marketplace.visualstudio.com/items?itemName=ms-kubernetes-tools.vscode-kubernetes-tools).

This plugin is currently in Preview Mode.

![](images/demo1.gif)

### Kubernetes & OpenShift resource tree
The plugin offers a tool window with a tree that displays all the resources that exist on a Kubernetes or OpenShift cluster.
The tree displays all the contexts that exist in your kube config (~/.kube/config).
The tree is dynamically updated when external tools like kubectl, oc, etc. change the kube config. 

#### Current Context
The tree displays resources that exist on the cluster that the current context points to. 
The user can switch the current context and cluster by choosing any context that's listed and picking "Set as Current Cluster" in the context menu.
OpenShift clusters are marked as such with an OpenShift icon instead of a Kubernetes icon.
 
#### Current Namespace/Project
The tree displays resources that exist within the current namespace as specified in the kube config or that are not bound to a namespace/project.
The user may switch the current namespace with the context menu item "Use Namespace". 
In OpenShift clusters the tree also lists projects which behave in an equivalent manner. 

#### Resource Categories
Resources are organized in different categories.

![img.png](images/categories.png)

#### Pods
Pods are marked as running if their icon has a green dot. 
A red dot, on the contrary, indicates that a pod is either pending, succeeded (terminated), failed or is in an unknown state.
Pods also unveil their IP address and the number of running out of the total number of containers.

![img.png](images/pod.png)

#### Edit Resources
You can edit any resource that is listed in the resource tree.
Either double click it or pick "Edit..." in the context menu. A Yaml editor opens up and allows you to edit the resource.

![editor](images/editor.png)

The editor allows you to push or pull the resource to or from the cluster. 
The toolbar holds corresponding actions.

![editor toolbar](images/editor-toolbar.png)

The editor provides schema validation for the basic kubernetes types.

![editor schema](images/editor-schema.png)

The editor keeps track of your changes, and the ones on the cluster.
Changing the yaml will prompt you to push your changes to the cluster.
Keep editing to the point where you are ready to update the cluster. 
Then either hit "Push" in the notification or use the push action in the toolbar.
Pushing updates the resource on the cluster so that it reflects your changes.

![editor push](images/editor-push.png)

The editor will also notify you of changes that happened on the cluster. 
If you didn't change anything in the editor yet, the new version is pulled automatically.

![editor pulled](images/editor-pulled.png)

Competing changes in your editor and on the cluster get notified with 2 options: 
You can overwrite the cluster by pushing the editor to the cluster.
Alternatively you can replace your local version with the one on the cluster and pull it.

![editor pull or push](images/editor-pull-push.png)

#### Create Resources
You currently cannot start from scratch and create a new resource. There's an easy workaround though:
Start by editing an existing resource. Once in the editor you can simply paste the yaml for your new resource.
The editor will then prompt you to push it to the cluster and create a new resource.

![editor push create new](images/editor-push-new.png)


#### Delete Resources
You may delete any resource that is listed in the resource tree by choosing "Delete" in the context menu.


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

UI Testing
==========
You can perform UI testing by running the following command:
```sh
./gradlew clean runIdeForUiTests -PideaVersion=IC-2020.2& ./gradlew integrationTest
```

License
=======
EPL 2.0, See [LICENSE](LICENSE) for more information.
