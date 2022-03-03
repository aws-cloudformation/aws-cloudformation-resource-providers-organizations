package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Tag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagTestResources {

    final static Set<Tag> defaultTags = new HashSet<Tag>(Arrays.asList(
        Tag.builder().key("Constant").value("Should remain").build(),
        Tag.builder().key("Update").value("Should be updated").build(),
        Tag.builder().key("Delete").value("Should be deleted").build()
    ));

    final static Set<Tag> updatedTags = new HashSet<Tag>(Arrays.asList(
        Tag.builder().key("Constant").value("Should remain").build(),
        Tag.builder().key("Update").value("Has been updated").build(),
        Tag.builder().key("Add").value("Should be added").build()
    ));

    static ListTagsForResourceResponse buildDefaultTagsResponse() {
        return ListTagsForResourceResponse.builder()
                .tags(defaultTags)
                .build();
    }

    static ListTagsForResourceResponse buildUpdatedTagsResponse() {
        return ListTagsForResourceResponse.builder()
                .tags(updatedTags)
                .build();
    }

    static ListTagsForResourceResponse buildEmptyTagsResponse() {
        return ListTagsForResourceResponse.builder()
                .build();
    }

    static Set<software.amazon.organizations.organizationalunit.Tag> translateTags(Set<Tag> tags) {
        if (tags == null) return new HashSet<>();

        final Set<software.amazon.organizations.organizationalunit.Tag> tagsToReturn = new HashSet<>();
        for (Tag inputTags : tags) {
            software.amazon.organizations.organizationalunit.Tag tag = software.amazon.organizations.organizationalunit.Tag.builder()
                        .key(inputTags.key())
                        .value(inputTags.value())
                        .build();
            tagsToReturn.add(tag);
        }

        return tagsToReturn;
    }
}
