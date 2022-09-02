# AWS::Organizations::Account AlternateContacts

Alternate contacts you want to put. Your organization must enable all features to manage settings on your member accounts. You need to enable trusted access for AWS Account Management service.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#billing" title="Billing">Billing</a>" : <i><a href="alternatecontact.md">AlternateContact</a></i>,
    "<a href="#operations" title="Operations">Operations</a>" : <i><a href="alternatecontact.md">AlternateContact</a></i>,
    "<a href="#security" title="Security">Security</a>" : <i><a href="alternatecontact.md">AlternateContact</a></i>
}
</pre>

### YAML

<pre>
<a href="#billing" title="Billing">Billing</a>: <i><a href="alternatecontact.md">AlternateContact</a></i>
<a href="#operations" title="Operations">Operations</a>: <i><a href="alternatecontact.md">AlternateContact</a></i>
<a href="#security" title="Security">Security</a>: <i><a href="alternatecontact.md">AlternateContact</a></i>
</pre>

## Properties

#### Billing

In order to keep the right people in the loop, you can add an alternate contact for billing, operations, and security communications.

_Required_: No

_Type_: <a href="alternatecontact.md">AlternateContact</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Operations

_Required_: No

_Type_: <a href="alternatecontact.md">AlternateContact</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Security

_Required_: No

_Type_: <a href="alternatecontact.md">AlternateContact</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)
