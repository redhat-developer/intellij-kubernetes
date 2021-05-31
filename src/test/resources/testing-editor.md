**Editor supports schema**
1. "Edit..." resource

-> editor has corresponding schema selected (bottom right combo "Schema:")

**Editor title is <resource-name>@<namespace-name>.yml**
1. "Edit..." resource

-> editor has title matching pattern <resource>@<namespace>.yml

**Editor file will rename file to <resource>@<namespace>(2).yml if name already used**
1. "Edit..." resource
1. "Edit..." other resource
1. editor 1: Ctrl + A, Ctrl + C
1. editor 2: Ctrl + v

-> editor has title matching pattern <resource-name>@<namespace-name>(2).yml

**Editor would not rename on startup**
1. "Edit..." resource
1. change metadata > name / metadata > namespace / kind
      -> push notification ("create new" not "update existing")
1. restart IJ

-> editor title is still the same. It was not renamed to <XXXX(1).yml> 

**Irrelevant change, no notification**
1. "Edit..." resource
1. add space after property value

-> no notification

**Push notification with "update existing"**
1. "Edit..." resource
1. add label
   -> push notification ("Update" not "Create")
1. hit "Push"

-> notification disappears
-> label was added
-> editor title unchanged

**Push notification with "create new"**
1. "Edit..." resource
1. change metadata > name / metadata > namespace / kind
   -> push notification ("create new" not "update existing")
1. hit "Push"

-> new resource appears in tree
-> editor title changes

**Error notification disappears**
1. "Edit..." resource
1. change kind to invalid value
   -> error notification ("Unsupported resource kind)
1. change kind to valid value

-> error notification disappears

**Push notification with "update existing" turns "create new"**
1. "Edit..." resource
1. add label
   -> push notification "update existing" (not "create")
1. change metadata > name

-> push notification with "create new"

**Deleted notification appears**
1. "Edit..." resource
1. delete resource (ctx action, console, kubectl)

-> deleted notification appears

**Modified notification appears**
1. "Edit..." resource
1. modify resource externally (console, kubectl)

-> modified notification appears

**Error notification appears when pasting invalid content**
1. "Edit..." resource
2. paste invalid yaml

**Error notification appears on startup**
1. have editor with invalid content
2. restart IJ

-> Error notification appears

**Error notification disappears when correcting invalid content**
1. have editor with invalid content
2. correct error/paste valid content

**Push notification with reload appears**
1. "Edit..." resource
1. modify resource (ex. change label)
   -> Push notification appears
1. modify resource externally

-> Push notification shows additional option "Reload"

**Reload -> push notification disappears**
1. "Edit..." resource
1. modify resource (ex. change label)
   -> Push notification appears
1. modify resource externally
   -> Push notification shows additional option "Reload"
1. hit "Reload"

-> editor shows new resource, notification disappears

**Push now -> push notification disappears**
1. "Edit..." resource
1. modify resource (ex. change label)
   -> Push notification appears
1. modify resource externally
   -> Push notification shows additional option "Reload"
1. hit "Push now"

-> editor stays the same, notification disappears
