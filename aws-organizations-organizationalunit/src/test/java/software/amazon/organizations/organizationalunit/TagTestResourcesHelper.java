package software.amazon.organizations.organizationalunit;

import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TagResourceRequest;
import software.amazon.awssdk.services.organizations.model.UntagResourceRequest;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagTestResourcesHelper {

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

    static Set<software.amazon.organizations.organizationalunit.Tag> translateOrganizationTagsToOrganizationalUnitTags(Set<Tag> tags) {
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

    static Set<Tag> translateOrganizationalUnitTagsToOrganizationTags(Set<software.amazon.organizations.organizationalunit.Tag> tags) {
        if (tags == null) return new HashSet<>();

        final Set<Tag> tagsToReturn = new HashSet<>();
        for (software.amazon.organizations.organizationalunit.Tag inputTags : tags) {
            Tag tag = Tag.builder()
                        .key(inputTags.getKey())
                        .value(inputTags.getValue())
                        .build();
            tagsToReturn.add(tag);
        }

        return tagsToReturn;
    }

    static boolean tagsEqual(Set<?> set1, Set<?> set2){
        if (set1 == null || set2 == null) {
            return false;
        }

        if (set1.size() != set2.size()) {
            return false;
        }

        return set1.containsAll(set2);
    }

    static boolean correctTagsInTagAndUntagRequests(Collection<Tag> tagsToAddOrUpdate, List<String> tagsToRemove) {
        boolean correctTagsInRequests = true;

        Set<String> tagsToAddOrUpdateKeys = new HashSet<String>();
        Set<String> tagsToRemoveKeys = new HashSet<String>();

        for (Tag tag : tagsToAddOrUpdate) {
            tagsToAddOrUpdateKeys.add(tag.key());
        }

        for (String key : tagsToRemove) {
            tagsToRemoveKeys.add(key);
        }

        // Constant tag should be in neither tagsToAddOrUpdate nor tagsToRemove
        if (tagsToAddOrUpdateKeys.contains("Constant") || tagsToRemoveKeys.contains("tagsToRemoveKeys")) {
            correctTagsInRequests = false;
        }

        // Delete tag should be the single item in tagsToRemove
        if (tagsToAddOrUpdateKeys.contains("Delete") || !tagsToRemoveKeys.contains("Delete") || tagsToRemoveKeys.size() != 1) {
            correctTagsInRequests = false;
        }

        // Update tag should be in tagsToAddOrUpdate not tagsToRemove
        if (!tagsToAddOrUpdateKeys.contains("Update") || tagsToRemoveKeys.contains("Update")) {
            correctTagsInRequests = false;
        }

        // Add tag should be in tagsToAddOrUpdate not tagsToRemove
        if (!tagsToAddOrUpdateKeys.contains("Add") || tagsToRemoveKeys.contains("Add")) {
            correctTagsInRequests = false;
        }

        return correctTagsInRequests;
    }
}
