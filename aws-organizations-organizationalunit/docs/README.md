# AWS::Organizations::OrganizationalUnit

Resource schema for AWS::Organizations::OrganizationalUnit

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::Organizations::OrganizationalUnit",
    "Properties" : {
        "<a href="#name" title="Name">Name</a>" : <i>String</i>,
        "<a href="#parentid" title="ParentId">ParentId</a>" : <i>String</i>,
        "<a href="#tags" title="Tags">Tags</a>" : <i>[ <a href="tag.md">Tag</a>, ... ]</i>
    }
}
</pre>

### YAML

<pre>
Type: AWS::Organizations::OrganizationalUnit
Properties:
    <a href="#name" title="Name">Name</a>: <i>String</i>
    <a href="#parentid" title="ParentId">ParentId</a>: <i>String</i>
    <a href="#tags" title="Tags">Tags</a>: <i>
      - <a href="tag.md">Tag</a></i>
</pre>

## Properties

#### Name

The friendly name of this OU.

_Required_: No

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>128</code>

_Pattern_: <code>[\s\S]*</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### ParentId

The unique identifier (ID) of the parent root or OU that you want to create the new OU in.

_Required_: No

_Type_: String

_Maximum_: <code>100</code>

_Pattern_: <code>^(r-[0-9a-z]{4,32})|(ou-[0-9a-z]{4,32}-[a-z0-9]{8,32})$</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Tags

A list of tags that you want to attach to the newly created OU.

_Required_: No

_Type_: List of <a href="tag.md">Tag</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the Id.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### Arn

The Amazon Resource Name (ARN) of this OU.

#### Id

The unique identifier (ID) associated with this OU.
