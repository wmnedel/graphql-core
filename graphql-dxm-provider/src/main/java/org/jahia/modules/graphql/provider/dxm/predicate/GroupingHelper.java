/*
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.graphql.provider.dxm.predicate;

import graphql.annotations.annotationTypes.GraphQLDescription;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Grouping logic
 *
 * @author akarmanov
 */
public class GroupingHelper {

    private static final String UNGROUPED_LIST = "theRest";

    public enum GroupingType {

        @GraphQLDescription("Put grouped items at the end in the order groups appear in the 'groups' list")
        END,

        @GraphQLDescription("Put grouped items at the start in the order groups appear in the 'groups' list")
        START
    }

    public static <T> Stream<T> group(Stream<T> stream, FieldGroupingInput fieldGroupingInput, FieldEvaluator fieldEvaluator) {

        List<T> originalList = stream.collect(Collectors.toList());
        //Create buckets for groups
        Map<String, List<T>> buckets = new LinkedHashMap<>();
        buckets.put(UNGROUPED_LIST, new ArrayList<>());
        for (String group : fieldGroupingInput.getGroups()) {
            buckets.put(group, new ArrayList<>());
        }

        //Distribute nodes among groups
        for (T node : originalList) {
            String groupByFieldValue = (String)fieldEvaluator.getFieldValue(node, fieldGroupingInput.getFieldName());
            if (buckets.containsKey(groupByFieldValue)) {
                buckets.get(groupByFieldValue).add(node);
            }
            else {
                buckets.get(UNGROUPED_LIST).add(node);
            }
        }

        List<T> groupedList = new ArrayList<>();

        //Concat grouped lists together in order they were processed
        for(Map.Entry<String, List<T>> entry : buckets.entrySet()) {
            if (!entry.getKey().equals(UNGROUPED_LIST)) {
                groupedList.addAll(entry.getValue());
            }
        }

        //Return grouped nodes either at start or end or original order
        if (fieldGroupingInput.getGroupingType().equals(GroupingType.END)) {
            buckets.get(UNGROUPED_LIST).addAll(groupedList);
            return buckets.get(UNGROUPED_LIST).stream();
        }
        else if (fieldGroupingInput.getGroupingType().equals(GroupingType.START)) {
            groupedList.addAll(buckets.get(UNGROUPED_LIST));
            return groupedList.stream();
        }

        return originalList.stream();
    }
}
