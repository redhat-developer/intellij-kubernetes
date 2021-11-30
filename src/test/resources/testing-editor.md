**Editor supports schema**
1. "Edit..." resource

-> editor has corresponding schema selected (bottom right combo "Schema:")

**Editor title is <resource-name>@<namespace-name>**
1. "Edit..." namespaced resource (ex. Pod)

-> editor title is matching pattern <resource-name>@<namespace-name>

**Editor title is <resource-name>**
1. "Edit..." non namespaced resource (ex. Namespace)

-> editor title is matching pattern <resource-name>

**Editor title is <filename>**
1. File > Open & pick local yaml/json file 

-> editor title displays the filename

**Editor title is not changed, normal behaviour**
1. File > Open & pick local xml-file OR yml file with non-kubernetes content (ex. helloworld.yml)
```
---
- name: This is a hello-world example
  hosts: ansibleclient01.local
  tasks:
   - name: Create a file called '/tmp/testfile.txt' with the content 'hello world'.
     copy:
     content: hello worldn
     dest: /tmp/testfile.txt
```

-> editor title displays the filename (= default), there's no action toolbar (for pushing/pulling from/to cluster)

**Irrelevant change, no notification**
1. "Edit..." resource
1. add space after property value

-> no notification

**Push notification "update existing"**
1. "Edit..." resource
1. add label
   -> push notification ("Update" not "Create")
1. hit "Push"

-> notification disappears
-> editor title unchanged

**Push notification "update existing" when changing name to existing**
1. "Edit..." resource
1. change metadata > name
1. push to server
   -> new resource shows up in tree
1. change metadata > name to initial name

-> Push notification changes to "update existing"
-> editor title unchanged

**Push notification "create new"**
1. "Edit..." resource
1. change metadata > name / namespace / kind 
   -> push notification ("create new" not "update existing")
1. hit "Push"

-> new resource appears in tree (if resource in current namespace was modified)
-> editor title changed to new name OR namespace

**Can push resource even if current namespace is different**
1. "Edit..." namespaced resource
1. "Use Namespace" on different Namespace   
1. in editor: change metadata > name
-> push notification "create new"
1. hit "Push"
-> resource does not appear in tree (current namespace is different)
1. "Use Namespace" on initial Namespace

-> new resource is visible in tree

**Push notification with "update existing" for custom resource**
1. "Edit..." custom resource
1. add label 

-> push notification ("update existing")

**Push notification with "update existing" for knative 'Service' custom resource**
1. Install knative tutorial https://redhat-developer-demos.github.io/knative-tutorial/knative-tutorial/
1. Make sure have "greeter" service at Custom Resources > services > greeter
1. "Edit..." Custom Resources > services > greeter
1. change name (ex. greeter2)
   -> push notification "create new"
1. hit "Push"

-> new Service "greeter2" appears in tree

**Push notification replaced by deleted notification**
1. "Edit..." resource
1. change/add label
1. delete resource (in tree, kubectl or console)

-> "Push existing resource" turns "Deleted on Cluster"

**Push notification with "update existing" turns "create new"**
1. "Edit..." resource
1. add label
   -> push notification "update existing" (not "create")
1. change metadata > name

-> push notification with "create new"

**Deleted notification appears on new editor**
1. "Edit..." resource
1. delete resource (ctx action/console/kubectl)

-> deleted notification appears

**Deleted notification replaced by Push notification**
1. "Edit..." resource
1. delete resource (ctx action/console/kubectl)
   -> deleted notification appears
1. change name

-> Push notification "create new"

**Automatic reload of editor & reloaded notification if local copy has no changes**
1. "Edit..." resource
1. modify resource externally (console, kubectl)

-> editor is reloaded (verify by watching metadata > `resourceVersion`)
-> reloaded notification appears

**Modified notification appears**
1. "Edit..." resource
1. add label 

1. modify resource externally (console, kubectl)

-> modified notification appears

**Reload -> Modified notification disappears**
1. "Edit..." resource
1. modify resource (ex. change label)
   -> Push notification appears
1. modify resource externally
   -> Push notification shows additional option "Reload"
1. hit "Reload"

-> editor shows new resource, notification disappears

**Error notification appears when pasting invalid content**
1. "Edit..." resource
2. paste invalid yaml

-> Error notification appears

**Error notification disaappears when correcting invalid content**
1. "Edit..." resource. Have an editor which starts with
```
---
apiVersion: 
```
2. Insert a " " before the "---"

-> Error notification appears
3. Remove the " " (content is now correct)

-> Error notification disappears

**Error notification appears when inserting invalid content**
1. "Edit..." resource
2. modify metadata: insert "aaaaa" on a new line right after metadata

```
metadata:
   aaaaaa
   annotations:
      machine.openshift.io/machine: "openshift-machine-api/ci-ln-yp4gpzk-f76d1-rgqp-master-0" 
```

-> Error notification appears

**Error notification appears on startup**
1. have editor with invalid content
2. restart IJ

-> Error notification appears

**Details in error notification shows cause**
1. "Edit..." resource
1. change kind to invalid value
   -> error notification ("Invalid kubernetes yaml/json")
1. Hit "Details"

-> Balloon shows cause

**Error notification disappears**
1. "Edit..." resource
1. change kind to invalid value
   -> error notification ("Invalid kubernetes yaml/json")
1. change kind to valid value

-> error notification disappears

**Push notification appears for local file**
1. File > New > YML file
2. paste the following into editor
```
apiVersion: batch/v1
kind: Job
metadata:
  name: countdown
spec:
  template:
    metadata:
      name: countdown
    spec:
      containers:
      - name: counter
        image: centos:7
        command:
         - "bin/bash"
         - "-c"
         - "for i in 9 8 7 6 5 4 3 2 1 ; do echo $i ; done"
      restartPolicy: Never
```
-> Push notification appears

**Pull notification appears when switching back to editor**
1. "Edit..." resource
2. select all && copy
3. File > New > YML File (ex. pod.yml)
4. paste
5. modify content (ex. add label)
6. push to cluster
7. switch back to editor with cluster resource

-> pull notification appears

**Change replicas causes new pods to appear in tree**
1. File > New > YML file
2. paste the following into editor
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sise-deploy
spec:
  replicas: 1
  selector:
    matchLabels:
      app: sise
  template:
    metadata:
      labels:
        app: sise
    spec:
      containers:
      - name: sise
        image: quay.io/openshiftlabs/simpleservice:0.5.0
        ports:
        - containerPort: 9876
        env:
        - name: SIMPLE_SERVICE_VERSION
          value: "0.9"
```
3. Push editor to cluster
4. Identify pod `sise-deploy-xxxx` in all 3 categories
 * [context] > Nodes > [node]
 * [context] > Workloads > Deployments > sise-deploy
 * [context] > Workloads > Pods
6. change `spec > replicas` to 2 & push

-> 2nd pod `sise-deploy-xxxx` appears in all 3 categories