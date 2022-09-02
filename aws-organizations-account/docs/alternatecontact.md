# AWS::Organizations::Account AlternateContact

In order to keep the right people in the loop, you can add an alternate contact for billing, operations, and security communications.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#emailaddress" title="EmailAddress">EmailAddress</a>" : <i>String</i>,
    "<a href="#name" title="Name">Name</a>" : <i>String</i>,
    "<a href="#phonenumber" title="PhoneNumber">PhoneNumber</a>" : <i>String</i>,
    "<a href="#title" title="Title">Title</a>" : <i>String</i>
}
</pre>

### YAML

<pre>
<a href="#emailaddress" title="EmailAddress">EmailAddress</a>: <i>String</i>
<a href="#name" title="Name">Name</a>: <i>String</i>
<a href="#phonenumber" title="PhoneNumber">PhoneNumber</a>: <i>String</i>
<a href="#title" title="Title">Title</a>: <i>String</i>
</pre>

## Properties

#### EmailAddress

Specifies an email address for the alternate contact.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>64</code>

_Pattern_: <code>^[\s]*[\w+=.#!&-]+@[\w.-]+\.[\w]+[\s]*$</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Name

Specifies a name for the alternate contact.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>64</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### PhoneNumber

Specifies a phone number for the alternate contact.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>25</code>

_Pattern_: <code>^[\s0-9()+-]+$</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Title

Specifies a title for the alternate contact.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>50</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)
