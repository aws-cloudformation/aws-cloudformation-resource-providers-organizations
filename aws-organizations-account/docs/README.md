# AWS::Organizations::Account

You can use AWS::Organizations::Account to manage accounts in organization.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::Organizations::Account",
    "Properties" : {
        "<a href="#accountname" title="AccountName">AccountName</a>" : <i>String</i>,
        "<a href="#email" title="Email">Email</a>" : <i>String</i>,
        "<a href="#rolename" title="RoleName">RoleName</a>" : <i>String</i>,
        "<a href="#parentids" title="ParentIds">ParentIds</a>" : <i>[ String, ... ]</i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>,
        "<a href="#alternatecontacts" title="AlternateContacts">AlternateContacts</a>" : <i><a href="alternatecontacts.md">AlternateContacts</a></i>,
    }
}
</pre>

### YAML

<pre>
Type: AWS::Organizations::Account
Properties:
    <a href="#accountname" title="AccountName">AccountName</a>: <i>String</i>
    <a href="#email" title="Email">Email</a>: <i>String</i>
    <a href="#rolename" title="RoleName">RoleName</a>: <i>String</i>
    <a href="#parentids" title="ParentIds">ParentIds</a>: <i>
      - String</i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
    <a href="#alternatecontacts" title="AlternateContacts">AlternateContacts</a>: <i><a href="alternatecontacts.md">AlternateContacts</a></i>
</pre>

## Properties

#### AccountName

The friendly name of the member account.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>50</code>

_Pattern_: <code>[\u0020-\u007E]+</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### Email

The email address of the owner to assign to the new member account.

_Required_: Yes

_Type_: String

_Minimum_: <code>6</code>

_Maximum_: <code>64</code>

_Pattern_: <code>[^\s@]+@[^\s@]+\.[^\s@]+</code>

_Update requires_: [Replacement](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-replacement)

#### RoleName

The name of an IAM role that AWS Organizations automatically preconfigures in the new member account. Default name is OrganizationAccountAccessRole if not specified.

_Required_: No

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>64</code>

_Pattern_: <code>[\w+=,.@-]{1,64}</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### ParentIds

List of parent nodes for the member account. Currently only one parent at a time is supported. Default is root.

_Required_: No

_Type_: List of String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

A list of tags that you want to attach to the newly created account. For each tag in the list, you must specify both a tag key and a value.

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### AlternateContacts

Alternate contacts you want to put. Your organization must enable all features to manage settings on your member accounts. You need to enable trusted access for AWS Account Management service.

_Required_: No

_Type_: <a href="alternatecontacts.md">AlternateContacts</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the AccountId.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### AccountId

If the account was created successfully, the unique identifier (ID) of the new account.

#### CreateAccountRequestId

Specifies the Id value that uniquely identifies the CreateAccount request. You can get the value from the CreateAccountStatus.Id response in an earlier CreateAccount request, or from the ListCreateAccountStatus operation.

#### FailureReason

If the create account request failed, a description of the reason for the failure.
