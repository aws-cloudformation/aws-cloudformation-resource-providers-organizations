package software.amazon.organizations.policy;

import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Tag;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TagTestResourceHelper {
    final static Tag CONSTANT_TAG = Tag.builder().key("Constant").value("Should remain").build();
    final static String DELETED_TAG_KEY = "Delete";
    final static Tag DELETED_TAG = Tag.builder().key(DELETED_TAG_KEY).value("Should be deleted").build();
    final static Tag ADDED_TAG = Tag.builder().key("Add").value("Should be added").build();
    final static Tag CHANGING_TAG_INITIAL = Tag.builder().key("Update").value("Should be updated").build();
    final static Tag CHANGING_TAG_UPDATED = Tag.builder().key("Update").value("Has been updated").build();

    final static Set<Tag> defaultTags = new HashSet<>(Arrays.asList(
        CONSTANT_TAG,
        CHANGING_TAG_INITIAL,
        DELETED_TAG
    ));

    final static Set<Tag> updatedTags = new HashSet<>(Arrays.asList(
        CONSTANT_TAG,
        CHANGING_TAG_UPDATED,
        ADDED_TAG
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

    static Set<software.amazon.organizations.policy.Tag> translateOrganizationTagsToPolicyTags(Set<Tag> tags) {
        if (tags == null) return new HashSet<>();

        final Set<software.amazon.organizations.policy.Tag> tagsToReturn = new HashSet<>();
        for (Tag inputTags : tags) {
            software.amazon.organizations.policy.Tag tag = software.amazon.organizations.policy.Tag.builder()
                .key(inputTags.key())
                .value(inputTags.value())
                .build();
            tagsToReturn.add(tag);
        }

        return tagsToReturn;
    }

    static Set<Tag> translatePolicyTagsToOrganizationTags(Set<software.amazon.organizations.policy.Tag> tags) {
        if (tags == null) return new HashSet<>();

        final Set<Tag> tagsToReturn = new HashSet<>();
        for (software.amazon.organizations.policy.Tag inputTags : tags) {
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

        Set<String> tagsToAddOrUpdateKeys = new HashSet<>();
        Set<String> tagsToRemoveKeys = new HashSet<>();

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
