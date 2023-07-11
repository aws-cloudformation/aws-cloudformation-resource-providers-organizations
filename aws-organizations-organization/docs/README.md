# AWS::Organizations::Organization

You can use AWS::Organizations::Organization to Creates an AWS organization, the account whose user is calling the CreateOrganization operation automatically becomes the management account of the new organization.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "Type" : "AWS::Organizations::Organization",
    "Properties" : {
        "<a href="#featureset" title="FeatureSet">FeatureSet</a>" : <i>String</i>,
    }
}
</pre>

### YAML

<pre>
Type: AWS::Organizations::Organization
Properties:
    <a href="#featureset" title="FeatureSet">FeatureSet</a>: <i>String</i>
</pre>

## Properties

#### FeatureSet

Specifies the feature set supported by the new organization. Each feature set supports different levels of functionality.

_Required_: No

_Type_: String

_Allowed Values_: <code>ALL</code> | <code>CONSOLIDATED_BILLING</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

## Return Values

### Ref

When you pass the logical ID of this resource to the intrinsic `Ref` function, Ref returns the Id.

### Fn::GetAtt

The `Fn::GetAtt` intrinsic function returns a value for a specified attribute of this type. The following are the available attributes and sample return values.

For more information about using the `Fn::GetAtt` intrinsic function, see [Fn::GetAtt](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html).

#### Id

The unique identifier (ID) of an organization.

#### Arn

The Amazon Resource Name (ARN) of an organization.

#### ManagementAccountArn

The Amazon Resource Name (ARN) of the account that is designated as the management account for the organization.

#### ManagementAccountId

The unique identifier (ID) of the management account of an organization.

#### ManagementAccountEmail

The email address that is associated with the AWS account that is designated as the management account for the organization.

#### RootId

The unique identifier (ID) for the root.
