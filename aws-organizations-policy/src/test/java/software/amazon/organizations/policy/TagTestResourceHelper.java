package software.amazon.organizations.policy;

import com.google.common.collect.ImmutableMap;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.Tag;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
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

    final static Map<String, String> defaultStackTags = ImmutableMap.of(
            "StackTagKey1", "StackTagValue1", "StackTagKey2", "StackTagValue2");
    final static Map<String, String> updatedStackTags = ImmutableMap.of(
            "StackTagKey1", "StackTagValue3", "StackTagKey4", "StackTagValue4");

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

    static boolean tagsEqual(Set<?> set1, Set<?> set2){
        if (set1 == null || set2 == null) {
            return false;
        }

        return set1.equals(set2);
    }

    static boolean correctTagsInTagAndUntagRequests(Set<Tag> tagsToAddOrUpdate, Set<String> tagsToRemoveKeys) {
        boolean correctTagsInRequests = true;

        Set<String> tagsToAddOrUpdateKeys = new HashSet<>();
        for (Tag tag : tagsToAddOrUpdate) {
            tagsToAddOrUpdateKeys.add(tag.key());
        }

        // Constant tag should be in neither tagsToAddOrUpdate nor tagsToRemove
        if (tagsToAddOrUpdateKeys.contains("Constant") || tagsToRemoveKeys.contains("tagsToRemoveKeys")) {
            correctTagsInRequests = false;
        }

        // Only Delete and StackTagKey2 tags should be in tagsToRemove
        if (tagsToAddOrUpdateKeys.contains("Delete") || tagsToAddOrUpdateKeys.contains("StackTagKey2") ||
                !tagsToRemoveKeys.contains("Delete") || !tagsToRemoveKeys.contains("StackTagKey2") || tagsToRemoveKeys.size() != 2) {
            correctTagsInRequests = false;
        }

        // Update and StackTagKey1 tags (getting updated) should be in tagsToAddOrUpdate and not in tagsToRemove
        if (!tagsToAddOrUpdateKeys.contains("Update") || !tagsToAddOrUpdateKeys.contains("StackTagKey1") ||
                tagsToRemoveKeys.contains("Update") || tagsToRemoveKeys.contains("StackTagKey1")) {
            correctTagsInRequests = false;
        }

        // Add and StackTagKey4 tags (getting added) should be in tagsToAddOrUpdate and not in tagsToRemove
        if (!tagsToAddOrUpdateKeys.contains("Add") || !tagsToAddOrUpdateKeys.contains("StackTagKey4") ||
                tagsToRemoveKeys.contains("Add") || tagsToRemoveKeys.contains("StackTagKey4")) {
            correctTagsInRequests = false;
        }

        return correctTagsInRequests;
    }
}
