package com.android.launcher3;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Enrico on 03/09/2017.
 */

class IconsSearchUtils {

    static void filter(String query, List<String> matchingIcons, List<String> allIcons, ChooseIconActivity.GridAdapter mGridAdapter) {

        //new array list that will hold the filtered data
        ArrayList<String> resultsFromAllIcons = new ArrayList<>();
        ArrayList<String> resultsFromMatchingIcons = new ArrayList<>();

        Boolean mNoMatchingDrawables = matchingIcons.isEmpty();

        if (query.isEmpty()) {

            resultsFromAllIcons.clear();
            resultsFromAllIcons.add(null);
            resultsFromAllIcons.addAll(allIcons);

            resultsFromMatchingIcons.clear();

            if (!mNoMatchingDrawables) {
                resultsFromMatchingIcons.add(null);
                resultsFromMatchingIcons.addAll(matchingIcons);
            }

            mGridAdapter.filterList(resultsFromAllIcons, resultsFromMatchingIcons);

        } else {

            resultsFromAllIcons.clear();
            resultsFromMatchingIcons.clear();

            if (mNoMatchingDrawables) {

                getFilteredResults(allIcons, resultsFromAllIcons, query);

            } else {

                resultsFromAllIcons.clear();
                resultsFromMatchingIcons.clear();

                getFilteredResults(allIcons, resultsFromAllIcons, query);
                getFilteredResults(matchingIcons, resultsFromMatchingIcons, query);
            }
            //calling a method of the adapter class and passing the filtered list
            mGridAdapter.filterList(resultsFromAllIcons, resultsFromMatchingIcons);
        }
    }

    private static void getFilteredResults(List<String> originalList, List<String> filteredResults, String query) {

        //looping through existing elements
        for (String iconTitle : originalList) {
            //if the existing elements contains the search input
            if (iconTitle.contains(query)) {
                //adding the element to filtered list
                filteredResults.add(iconTitle);
            }
        }
    }
}
