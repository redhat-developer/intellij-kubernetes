**Editor supports schema**
1. "Edit..." resource

-> editor has corresponding schema selected (bottom right combo "Schema:")

**Editor title is [resource-name]@[namespace-name]**
1. "Edit..." namespaced resource (ex. Pod)

-> editor title is matching pattern <resource-name>@<namespace-name>

**Editor title is [resource-name]**
1. "Edit..." non namespaced resource (ex. Namespace)

-> editor title is matching pattern <resource-name>

**Editor title is [filename]**
1. File > Open & pick local yaml/json file 

-> editor title displays the filename

**Editor title changes to new name as you type**
1. "Edit..." resource (ex. Pod)
2. Change metadata > name

3. -> editor title is changing to new name as you type

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

**Push notification "create new"**
1. "Edit..." resource
1. change metadata > name / namespace / kind
   -> Push notification "create new"
1. hit "Push"

-> new resource appears in tree (if resource in current namespace was modified)
-> notification disappears

**Push notification "update existing" for existing resource**
1. have 2 resources (ex 2 Pods)
1. "Edit..." resource 1
1. change metadata > name to name of resource 2
   -> Push or Pull notification is shown

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

-> push notification "update existing"

**Push notification for file that contains custom resource without namespace**
1. make sure you have tekton installed ([Setup Tekton](https://redhat-scholars.github.io/tekton-tutorial/tekton-tutorial/setup.html))
1. make sure there's no task "foo" on cluster
1. File > New > foo.yml
1. paste the following into editor
```
---
kind: "Task"
apiVersion: "tekton.dev/v1beta1"
metadata:
  name: "foo"
spec:
  params:
  - default: "/workspace/workspace/Dockerfile"
    description: "The path to the dockerfile to build"
    name: "pathToDockerFile"
    type: "string"
  resources:
    inputs:
    - name: "workspace"
      type: "git"
    outputs:
    - name: "buildImage"
      type: "image"
  steps:
  - args:
    - "-c"
    - "echo hello world"
    command:
    - "/bin/bash"
    image: "fedora"
    name: "build-sources"
    resources: {}
```
-> push notification "create new"

**Pull notification "update existing" for new file with existing resource**
1. make sure there's a task "foo" on cluster
2. File > New > foo.yml
3. paste yaml in previous use case

-> Pull notification with "Push" link

**Push notification with "update existing" for knative 'Service' custom resource**
3. Install knative tutorial https://redhat-developer-demos.github.io/knative-tutorial/knative-tutorial/
4. Make sure have "greeter" service at Custom Resources > services > greeter
5. "Edit..." Custom Resources > services > greeter
6. change name (ex. greeter2)
   -> push notification "create new"
7. hit "Push"

-> new Service "greeter2" appears in tree

**Deleted notification appears on new editor**
1. "Edit..." resource
1. delete resource (ctx action/console/kubectl)

-> deleted notification appears

**Push "update existing" replaced by Deleted notification**
1. "Edit..." deployment
1. change/add label
   -> notification "Push updated existing" appears 
1. delete deployment (in tree, kubectl or console)

-> "Push update existing" replaced by "Deleted on Cluster"

**Push notification appears**
1. "Edit..." resource
1. add label
   -> "Push update existing" notification
1. modify resource externally (console, kubectl)

-> Push notification appears

**Push "create new" after resource deletion**
1. "Edit..." deployment
1. change metadata > name
   -> "Push create new" appears
1. delete (initially edited) deployment
1. change metadata > name to initial name

-> "Push create new" should still be visible

**Pull notification appears**
1. "Edit..." resource
1. modify resource externally (console, kubectl)

-> Pull notification appears with links "Pull", "Push", "Ignore"

**Pull -> Push notification disappears**
1. "Edit..." resource
1. modify resource (ex. change label)
   -> Push notification appears
1. modify resource externally
   -> Push notification shows additional option "Pull"
1. hit "Pull"

-> editor shows new resource, notification disappears

**Error notification appears when pasting invalid content**
1. "Edit..." resource
2. paste invalid yaml

-> Error notification appears

**Error notification disappears when correcting invalid content**
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

**Error notification appears for resource without name**
1. File > New > YML file
2. paste the following resource without name
```
apiVersion: v1
metadata:
  labels:
    jedi: yoda
data:
  username: YWRtaW4=222
  password: MWYyZDFlMmU2N2Rm
kind: Secret
type: Opaque
```

-> Error notification resource could not be retrieved (details: name missing)

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
1. paste the following into editor
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
1. Push editor to cluster
1. Identify pod `sise-deploy-xxxx` in all 3 categories
 * [context] > Nodes > [node]
 * [context] > Workloads > Deployments > sise-deploy
 * [context] > Workloads > Pods
1. change `spec > replicas` to 2 & push

-> 2nd pod `sise-deploy-xxxx` appears in all 3 categories