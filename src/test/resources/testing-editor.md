**Editor supports schema**
1. "Edit..." resource

-> editor has corresponding schema selected (bottom right combo "Schema:")

**Editor title is <resource-name>@<namespace-name>.yml**
1. "Edit..." namespaced resource (ex. Pod)

-> editor title is matching pattern <resource-name>@<namespace-name>.yml

**Editor file will rename file to <resource>@<namespace>(2).yml if name already used**
1. "Edit..." resource
1. "Edit..." other resource
1. editor 1: Ctrl + A, Ctrl + C
1. editor 2: Ctrl + v

-> editor has title matching pattern <resource-name>@<namespace-name>(2).yml

**Editor title is <resource-name>.yml**
1. "Edit..." non namespaced resource (ex. Namespace)

-> editor title is matching pattern <resource-name>.yml

**Editor does not rename resource-file on startup**
1. "Edit..." resource
1. change metadata > name / metadata > namespace / kind
      -> push notification ("create new" not "update existing")
1. restart IJ

-> editor title is still the same. It was not renamed to <XXXX(1).yml> (was bug at some point)

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

**Push notification "update existing" when chaning name to existing**
1. "Edit..." resource
1. change metadata > name
1. push to server
   -> new resource shows up in tree
1. change metadata > name to initial name

-> editor title is <initial-name>(2)
-> editor shows push notification "update existing"

-> notification disappears
-> editor title unchanged

**Push notification "create new"**
1. "Edit..." resource
1. change metadata > name / metadata > namespace / kind 
   -> push notification ("create new" not "update existing")
1. hit "Push"

-> new resource appears in tree (if resource in current namespace was modified)
-> editor title changes

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

**Push notification with "update existing" turns "create new"**
1. "Edit..." resource
1. add label
   -> push notification "update existing" (not "create")
1. change metadata > name

-> push notification with "create new"

**Deleted notification appears**
1. "Edit..." resource
1. delete resource (ctx action/console/kubectl)

-> deleted notification appears

**Modified notification appears**
1. "Edit..." resource
1. modify resource externally (console, kubectl)

-> modified notification appears

**Modified notification replaced Push notification**
1. "Edit..." resource
1. modify resource (ex. change label)
   -> Push notification appears
1. modify resource externally

-> Modified notification replaced push notification

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

