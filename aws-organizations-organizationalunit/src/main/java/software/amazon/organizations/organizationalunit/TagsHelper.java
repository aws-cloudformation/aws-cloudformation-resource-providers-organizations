package software.amazon.organizations.organizationalunit;

import com.google.common.collect.Sets;
import software.amazon.awssdk.services.organizations.model.Tag;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TagsHelper {

    static Set<Tag> convertOrganizationalUnitTagToOrganizationTag(final Set<software.amazon.organizations.organizationalunit.Tag> tags) {
        final Set<Tag> tagsToReturn = new HashSet<>();
        if (tags != null) {
            for (software.amazon.organizations.organizationalunit.Tag inputTag : tags) {
                Tag tag = buildTag(inputTag.getKey(), inputTag.getValue());
                tagsToReturn.add(tag);
            }
        }
        return tagsToReturn;
    }

    static Set<String> getTagKeysToRemove(
            Set<Tag> oldTags, Set<Tag> newTags) {
        final Set<String> oldTagKeys = getTagKeySet(oldTags);
        final Set<String> newTagKeys = getTagKeySet(newTags);
        return Sets.difference(oldTagKeys, newTagKeys);
    }

    private static Set<String> getTagKeySet(Set<Tag> oldTags) {
        return oldTags.stream().map(Tag::key).collect(Collectors.toSet());
    }

    static Set<Tag> getTagsToAddOrUpdate(
            Set<Tag> oldTags, Set<Tag> newTags) {
        return Sets.difference(newTags, oldTags);
    }

    static Set<Tag> mergeTags(final Set<Tag> resourceTags, final Map<String, String> desiredResourceTags) {
        final Set<Tag> result = (resourceTags == null) ? new HashSet<>() : new HashSet<>(resourceTags);
        if (desiredResourceTags != null) {
            result.addAll(tagMapToTagSetConverter(desiredResourceTags));
        }
        return result;
    }

    private static Set<Tag> tagMapToTagSetConverter(final Map<String, String> map) {
        return map.entrySet()
                .stream()
                .map(entry -> buildTag(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());
    }

    static Tag buildTag(String key, String value) {
        return Tag.builder()
                .key(key)
                .value(value)
                .build();
    }
}
