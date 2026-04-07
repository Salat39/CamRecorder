package com.salat.navigation.transitions

import com.salat.navigation.transitions.entity.TransitionKey
import com.salat.navigation.transitions.entity.TransitionRule

/**
 * A set of data that defines animation transitions between screens
 */
val transitionsMap: Map<TransitionKey, TransitionRule> = emptyList<TransitionRule>(
//    TransitionRule(
//        enter = SPLIT_LIST_NAV_ROUTE_NAME,
//        exit = SPLIT_ADD_NAV_ROUTE_NAME,
//        invert = false,
//        type = TransitionType.DELAYED_SLIDE
//    ),
).associateBy { rule ->
    TransitionKey(rule.enter, rule.exit, rule.graph)
}
