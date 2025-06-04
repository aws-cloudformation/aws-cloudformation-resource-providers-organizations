package software.amazon.organizations.account;

import com.google.common.collect.Sets;
import software.amazon.awssdk.services.organizations.model.Tag;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TagsHelper {

    static Set<Tag> convertAccountTagToOrganizationTag(final Set<software.amazon.organizations.account.Tag> tags) {
        final Set<Tag> tagsToReturn = new HashSet<>();
        if (tags != null) {
            for (software.amazon.organizations.account.Tag inputTag : tags) {
                Tag tag = buildTag(inputTag.getKey(), inputTag.getValue());
                tagsToReturn.add(tag);
            }
        }
        return tagsToReturn;
    }

    static Set<String> getTagKeysToRemove(
            Set<Tag> existingTags, Set<Tag> requestedTags) {
        final Set<String> existingTagKeys = getTagKeySet(existingTags);
        final Set<String> requestedTagKeys = getTagKeySet(requestedTags);
        return Sets.difference(existingTagKeys, requestedTagKeys);
    }

    private static Set<String> getTagKeySet(Set<Tag> existingTags) {
        return existingTags.stream().map(Tag::key).collect(Collectors.toSet());
    }

    static Set<Tag> getTagsToAddOrUpdate(
            Set<Tag> existingTags, Set<Tag> requestedTags) {
        return Sets.difference(requestedTags, existingTags);
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
