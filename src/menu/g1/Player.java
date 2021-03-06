package menu.g1;

import java.text.BreakIterator;
import java.util.*;

import menu.sim.*;
import menu.sim.Food.FoodType;
import menu.sim.Food.MealType;

public class Player extends menu.sim.Player {
	/**
	 * Player constructor
	 *
	 * @param weeks             number of weeks
	 * @param numFamilyMembers  number of family members
	 * @param capacity          pantry capacity
	 * @param seed              random seed
	 * @param simPrinter        simulation printer
	 *
	 */

	private final Integer DAYS_PER_WEEK = 7;
	private Weighter weighter;
	private Map<MealType, Map<FamilyMember, ArrayList<FoodType>>> optimisticPlanner;
	private FamilyTracker familyTracker;
	private Map<MealType, Integer> maxCapacities;
	private Integer totalWeeks;

	
	public Player(Integer weeks, Integer numFamilyMembers, Integer capacity, Integer seed, SimPrinter simPrinter) {
		super(weeks, numFamilyMembers, capacity, seed, simPrinter);
		this.optimisticPlanner = new HashMap<>();
		this.weighter = new Weighter(numFamilyMembers);
		this.familyTracker = new FamilyTracker();
		this.maxCapacities = getMaxCapacitiesForMealTypes(capacity, numFamilyMembers);
		this.totalWeeks = weeks;
	}

	private Map<MealType, Integer> getMaxCapacitiesForMealTypes(Integer capacity, Integer numFamilyMembers) {
		Map<MealType, Integer> maxCapForMealTypes = new HashMap<>();
		maxCapForMealTypes.put(MealType.BREAKFAST, capacity - 14*numFamilyMembers);
		maxCapForMealTypes.put(MealType.LUNCH, 7*numFamilyMembers);
		maxCapForMealTypes.put(MealType.DINNER, 7*numFamilyMembers);

		return maxCapForMealTypes;
	}

	/**
	 * Create shopping list of meals to stock pantry
	 *
	 * @param week           current week
	 * @param numEmptySlots  number of empty slots left in the pantry
	 * @param familyMembers  all family members
	 * @param pantry         pantry inventory of remaining foods
	 * @param mealHistory    history of previous meal allocations
	 * @return               shopping list of foods to order
	 *
	 */

	public ShoppingList stockPantry(Integer week, Integer numEmptySlots, List<FamilyMember> familyMembers, Pantry pantry, MealHistory mealHistory) {
		if (week == 1) {
			familyTracker.init(familyMembers);
		}
		familyTracker.update(week, mealHistory);

		weighter.update(week, mealHistory, familyMembers);
		resetOptimisticPlanner(familyMembers);

		ShoppingList shoppingList = new ShoppingList();
		shopBreakfast(week, shoppingList, pantry, familyMembers);
		//addMeals(MealType.BREAKFAST, familyMembers, shoppingList);
		addMeals(MealType.LUNCH, familyMembers, shoppingList);
		shopDinner(shoppingList);

		return shoppingList;
	}

	private void shopBreakfast(Integer week,
							   ShoppingList shoppingList,
							   Pantry pantry,
							   List<FamilyMember> familyMembers) {
		Integer spotsForBreakfast = calculateNewCapacityFor(MealType.BREAKFAST, pantry);
		Integer minBreakfastsNeeded = getMinBreakfastsNeeded(pantry, numFamilyMembers);
		Map<MemberName, Integer> memberAllocations = getMemberAllocations(familyMembers, spotsForBreakfast);

		shoppingList.addLimit(MealType.BREAKFAST, spotsForBreakfast);
		addFirstChoiceBreakfasts(shoppingList, memberAllocations);
		if (minBreakfastsNeeded > 0) {
			addBackupBreakfasts(shoppingList, spotsForBreakfast);
		}
		simPrinter.println("Current pantry size: " + pantry.getNumAvailableMeals(MealType.BREAKFAST));
		simPrinter.println("Breakfast shopping list: ");
		simPrinter.println(shoppingList.getMealOrder(MealType.BREAKFAST).toString());
	}

	private void addFirstChoiceBreakfasts(ShoppingList shoppingList, Map<MemberName, Integer> memberAllocations) {
		PriorityQueue<MemberTracker> memberTrackers = familyTracker.getMembersByAvgSatisfaction();
		while (!memberTrackers.isEmpty()) {
			MemberTracker memberTracker = memberTrackers.poll();
			FoodType firstChoice = memberTracker.getFirstChoice(MealType.BREAKFAST);
			Integer quantity = memberAllocations.get(memberTracker.getName());
			addFoodToOrder(shoppingList, firstChoice, quantity);
		}
	}

	private void addBackupBreakfasts(ShoppingList shoppingList, Integer spotsOpen) {
		PriorityQueue<FoodScore> breakfasts = familyTracker.getBreakfastsByCompositeScore();
		while (!breakfasts.isEmpty()) {
			FoodType breakfast = breakfasts.poll().getFoodType();
			addFoodToOrder(shoppingList, breakfast, spotsOpen);
		}
	}

	private Integer getMinBreakfastsNeeded(Pantry pantry, Integer numFamilyMembers) {
		Integer breakfastsServedPerWeek = numFamilyMembers * 7;
		Integer breakfastInPantry = 0;
		for(FoodType foodType : Food.getFoodTypes(MealType.BREAKFAST)) {
			int numAvailableMeals = pantry.getNumAvailableMeals(foodType);
			breakfastInPantry += numAvailableMeals; 
		}
		if (breakfastsServedPerWeek > breakfastInPantry) {
			return breakfastsServedPerWeek - breakfastInPantry;
		}
		return 0;
	}

	private Map<MemberName, Integer> getMemberAllocations(List<FamilyMember> familyMembers, Integer breakfastCapacity) {
		Map<MemberName, Integer> memberAllocations = new HashMap<>();
		PriorityQueue<MemberTracker> memberTrackers = familyTracker.getMembersByAvgSatisfaction();
		Double totalWeight = 0.0;
		Integer totalAllocations = 0;

		while (!memberTrackers.isEmpty()) {
			MemberTracker memberTracker = memberTrackers.poll();
			totalWeight += memberTracker.getWeight();
		}		
		
		for (FamilyMember familyMember: familyMembers) {
			MemberTracker memberTracker = familyTracker.getMemberTracker(familyMember.getName());
			Integer allocationScore = (int) Math.round(memberTracker.getWeight()/totalWeight * breakfastCapacity);
			while ((totalAllocations + allocationScore > breakfastCapacity) && allocationScore > 0) {
				allocationScore--;
			}
			memberAllocations.put(familyMember.getName(),  allocationScore);
		}

		return memberAllocations;
	}

	private Integer calculateNewCapacityFor(MealType mealType, Pantry pantry) {
		Integer maxCapacity = maxCapacities.get(mealType);
		Integer breakfastInPantry = 0;
		for(FoodType foodType : Food.getFoodTypes(MealType.BREAKFAST)) {
			int numAvailableMeals = pantry.getNumAvailableMeals(foodType);
			breakfastInPantry += numAvailableMeals;
		}
		return maxCapacity - breakfastInPantry;
	}

	private void shopDinner(ShoppingList shoppingList) {
		// TODO: Pick based on each day
		shoppingList.addLimit(MealType.DINNER, DAYS_PER_WEEK * numFamilyMembers);
		PriorityQueue<FoodScore> dinners = familyTracker.getFoodsByCompositeScore(MealType.DINNER, 6);
		simPrinter.println("DINNERS SIZE: " + Integer.toString(dinners.size()));
		while (!dinners.isEmpty()) {
			FoodType dinner = dinners.poll().getFoodType();
			addFoodToOrder(shoppingList, dinner, numFamilyMembers);
		}
	}

	// does not work with dinner meals
	private void addMeals(MealType mealType, List<FamilyMember> familyMembers, ShoppingList shoppingList) {
		Map<FamilyMember, PriorityQueue<FoodScore>> memberScores = weighter.getMemberScoresFor(mealType);

		shoppingList.addLimit(mealType, DAYS_PER_WEEK * numFamilyMembers);
		ArrayList<FoodType> addedFoods = new ArrayList<>();

		for (FamilyMember member: familyMembers) {
			FoodType food = memberScores.get(member).poll().getFoodType();
			addFoodToOrder(shoppingList, food, DAYS_PER_WEEK);
			optimisticPlanner.get(mealType).get(member).add(food);
			addedFoods.add(food);
		}

		PriorityQueue<FoodScore> avgScores = weighter.getAvgScoresFor(mealType);
		while (!avgScores.isEmpty()) {
			FoodType food = avgScores.poll().getFoodType();
			if (!addedFoods.contains(food)) {
				addFoodToOrder(shoppingList, food, DAYS_PER_WEEK);
			}
		}
	}

	private void addDinner(ShoppingList shoppingList) {
		shoppingList.addLimit(MealType.DINNER, DAYS_PER_WEEK * numFamilyMembers);
		PriorityQueue<FoodScore> avgScores = weighter.getAvgScoresFor(MealType.DINNER);
		while (!avgScores.isEmpty()) {
			FoodType food = avgScores.poll().getFoodType();
			addFoodToOrder(shoppingList, food, numFamilyMembers);
		}
	}

	// returns array of family members who did not get their first choice of food
	private ArrayList<FamilyMember> addFirstChoices(Planner planner, Pantry pantry, MealType mealType) {
		Map<FamilyMember, ArrayList<FoodType>> optimisticPlan = optimisticPlanner.get(mealType);
		ArrayList<FamilyMember> noMealAssigned = new ArrayList<>();

		for (Map.Entry<FamilyMember, ArrayList<FoodType>> firstChoice: optimisticPlan.entrySet()) {
			FamilyMember member = firstChoice.getKey();
			// TODO: switch to tackle changes in food per day
			FoodType food = firstChoice.getValue().get(0);
			if (pantry.getNumAvailableMeals(food) >= DAYS_PER_WEEK) {
				addFoodToPlanner(planner, member, food);
			}
			else {
				noMealAssigned.add(member);
			}
		}
		return noMealAssigned;
	}
	

	/**
	 * Plan meals
	 *
	 * @param week           current week
	 * @param familyMembers  all family members
	 * @param pantry         pantry inventory of remaining foods
	 * @param mealHistory    history of previous meal allocations
	 * @return               planner of assigned meals for the week
	 *
	 */

	public Planner planMeals(Integer week, List<FamilyMember> familyMembers, Pantry pantry, MealHistory mealHistory) {

		Pantry originalPantry = pantry.clone();
		

		List<MemberName> memberNames = new ArrayList<>();
		List<FamilyMember> weightedPreferences = new ArrayList<>(familyMembers);

		Map<MealType, List<MemberName>> memberPriorityList = new LinkedHashMap<>();

		Map<MemberName, Map<MealType, Map<FoodType, Double>>> orderedFamilyPreferences = new HashMap<>();

		for(FamilyMember familyMember : familyMembers)
			memberNames.add(familyMember.getName());

		Planner planner = new Planner(memberNames);
		

		updateFamilyPreferenceMap(pantry, familyMembers, orderedFamilyPreferences);
		updateMemberPriorityList(familyMembers, memberPriorityList, orderedFamilyPreferences);

		//simPrinter.println("PANTRY: " + pantry.getMealsMap().get(MealType.BREAKFAST));
		simPrinter.println("PANTRY: " + pantry.getMealsMap().get(MealType.LUNCH));
		//simPrinter.println("PANTRY: " + pantry.getMealsMap().get(MealType.DINNER));

		
		//simPrinter.println("Order: " + memberPriorityList.get(MealType.BREAKFAST));
		for (MemberName member : orderedFamilyPreferences.keySet()){
			//simPrinter.println("\t\t" + member + ": " + orderedFamilyPreferences.get(member).get(MealType.BREAKFAST));
			simPrinter.println("\t\t" + member + ": " + orderedFamilyPreferences.get(member).get(MealType.LUNCH));
		}
		
		for(Day day : Day.values()){
			for(MemberName memberName : memberPriorityList.get(MealType.BREAKFAST)){
				if (pantry.getNumAvailableMeals(MealType.BREAKFAST) > 0){
					FoodType food = getBestFood(MealType.BREAKFAST, memberName, orderedFamilyPreferences);
					planner.addMeal(day, memberName, MealType.BREAKFAST, food);
					pantry.removeMealFromInventory(food);
				}
				if (pantry.getNumAvailableMeals(MealType.LUNCH) > 0){
					updateLunchPreferences(week, day, memberName, planner, mealHistory, orderedFamilyPreferences);
					FoodType food = getBestFood(MealType.LUNCH, memberName, orderedFamilyPreferences);
					planner.addMeal(day, memberName, MealType.LUNCH, food);
					pantry.removeMealFromInventory(food);
				}
				updateFamilyPreferenceMap(pantry, weightedPreferences, orderedFamilyPreferences);
				updateMemberPriorityList(weightedPreferences, memberPriorityList, orderedFamilyPreferences);
			}
			
		}

		planDinners(planner, pantry, familyMembers);

		simPrinter.println("\n\n\n********* PLANNER ********\n");
		//for(MealType meal : Food.getAllMealTypes()){
			simPrinter.println("LUNCH: ");
			for(Day day : Day.values()){
				simPrinter.println("\tDAY: " + day); 
				for(MemberName memberName : memberNames){
					simPrinter.println("\t\t"+ memberName + ": " + planner.getPlan().get(day).get(memberName).get(MealType.LUNCH));
				}
			}
		//}

		if(Player.hasValidPlanner(planner, originalPantry))
			return planner;
		return new Planner();
	}
	
	private void updateLunchPreferences(Integer week, Day thisDay, MemberName name, Planner planner, MealHistory mealHistory, Map<MemberName, Map<MealType, Map<FoodType, Double>>> orderedFamilyPreferences){
		int todayNumber = (week - 1) * 7 + new ArrayList<>(Arrays.asList(Day.values())).indexOf(thisDay) + 1;

		MealHistory weightedMealHistory = mealHistory;

		Map<Day, Map<MemberName, Map<MealType, FoodType>>> plan = planner.getPlan();
		Map<FoodType, Double> sortedLunchPreferences = new LinkedHashMap<>();

		simPrinter.println("\n\nOLD \t\t" + name + ": " + orderedFamilyPreferences.get(name).get(MealType.LUNCH));

		for (Day day : plan.keySet())
			weightedMealHistory.addDailyFamilyMeal(week, day, name, plan.get(day).get(name));

		Map<Integer, Map<MemberName, Map<MealType, FoodType>>> dailyMealsMap = weightedMealHistory.getDailyFamilyMeals();

		for(FoodType food : orderedFamilyPreferences.get(name).get(MealType.LUNCH).keySet()){

			Integer lastServedDay = 0;
			for (Integer dayNumber : dailyMealsMap.keySet())
				if (dailyMealsMap.get(dayNumber).get(name).get(MealType.LUNCH) == food && dayNumber > lastServedDay)
					lastServedDay = dayNumber;	

			if (lastServedDay > 0){
				
				Integer daysAgo = (todayNumber - lastServedDay);
				Double scale = (double) daysAgo / (daysAgo + 1);

				//simPrinter.println("daysAgo:  " + daysAgo + "\nlastServedDay: " + lastServedDay + "\nTodaynumber: " + todayNumber + "\nThisDay: " + thisDay + "\nThisWeek:" +week+ "\nScale: " + scale);
				orderedFamilyPreferences.get(name)
							.get(MealType.LUNCH)
							.replace(food, orderedFamilyPreferences.get(name)
										 	       .get(MealType.LUNCH)
										               .get(food) * scale);

				
			}
		}
		orderedFamilyPreferences.get(name).get(MealType.LUNCH)
					.entrySet()
					.stream()
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
		  			.forEachOrdered(x -> sortedLunchPreferences.put(x.getKey(), x.getValue()));
		orderedFamilyPreferences.get(name).replace(MealType.LUNCH, sortedLunchPreferences);
		simPrinter.println("NEW \t\t" + name + ": " + orderedFamilyPreferences.get(name).get(MealType.LUNCH));
	}

	private void planDinners(Planner planner, Pantry pantry, List<FamilyMember> familyMembers) {
		Map<FoodType, Integer> dinnerInventory = pantry.getMealsMap().get(MealType.DINNER);
		PriorityQueue<FoodScore> dinners = familyTracker.getDinnersByCompositeScore(6, dinnerInventory);

		for (Day day: Day.values()) {
			if (!dinners.isEmpty()) {
				FoodType dinner = dinners.poll().getFoodType();
				Integer quantity = dinnerInventory.get(dinner);
				for (int i = 0; i < quantity; i++) {
					FamilyMember member = familyMembers.get(i);
					planner.addMeal(day, member.getName(), MealType.DINNER, dinner);
				}
			}
		}
	}

	private void updateFamilyPreferenceMap(Pantry pantry, List<FamilyMember> familyMembers, Map<MemberName, Map<MealType, Map<FoodType, Double>>> orderedFamilyPreferences){

		orderedFamilyPreferences.clear();

		for(FamilyMember familyMember : familyMembers){

			Map<FoodType, Double> memberPrefs = familyMember.getFoodPreferenceMap();
			Map<FoodType, Double> breakfastPrefs = new HashMap<>();
			Map<FoodType, Double> lunchPrefs = new HashMap<>();
			Map<FoodType, Double> dinnerPrefs = new HashMap<>();

			orderedFamilyPreferences.put(familyMember.getName(), new LinkedHashMap<>());
			orderedFamilyPreferences.get(familyMember.getName()).put(MealType.BREAKFAST, new LinkedHashMap<>());
			orderedFamilyPreferences.get(familyMember.getName()).put(MealType.LUNCH, new LinkedHashMap<>());
			orderedFamilyPreferences.get(familyMember.getName()).put(MealType.DINNER, new LinkedHashMap<>());

			for(FoodType thisFood : Food.getAllFoodTypes()){
				switch(Food.getMealType(thisFood)) {
					case BREAKFAST:
						if(pantry.getNumAvailableMeals(thisFood) > 0)
							breakfastPrefs.put(thisFood, memberPrefs.get(thisFood));
						break; 
					case LUNCH:
						if(pantry.getNumAvailableMeals(thisFood) > 0)
							lunchPrefs.put(thisFood, memberPrefs.get(thisFood));	
						break; 
					case DINNER:
						if(pantry.getNumAvailableMeals(thisFood) > 2)
							dinnerPrefs.put(thisFood, memberPrefs.get(thisFood));
						break;
				}
			}

			for (MealType meal : Food.getAllMealTypes()){
				switch(meal) {
					case BREAKFAST:
						breakfastPrefs.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
							.forEachOrdered(x -> orderedFamilyPreferences
													.get(familyMember.getName())
													.get(meal)
													.put(x.getKey(), x.getValue()));
						break; 
					case LUNCH:
						lunchPrefs.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
							.forEachOrdered(x -> orderedFamilyPreferences
													.get(familyMember.getName())
													.get(meal)
													.put(x.getKey(), x.getValue()));
						break; 
					case DINNER:
						dinnerPrefs.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
							.forEachOrdered(x -> orderedFamilyPreferences
													.get(familyMember.getName())
													.get(meal)
													.put(x.getKey(), x.getValue()));
						break;
				}
			}
		}
	}

	private void updateMemberPriorityList(List<FamilyMember> familyMembers, Map<MealType, List<MemberName>> memberPriorityList, Map<MemberName, Map<MealType, Map<FoodType, Double>>> orderedFamilyPreferences){
		
		memberPriorityList.clear();

		for (MealType meal : Food.getAllMealTypes()){
			List<MemberName> membersOrder = new ArrayList<>();
			while(membersOrder.size() < familyMembers.size()){
				double bestPref = -1.0;
				MemberName lowestMember = null;
				for(FamilyMember familyMember : familyMembers){
					Map<FoodType, Double> memberFoodPrefs = orderedFamilyPreferences.get(familyMember.getName()).get(meal);

					if (memberFoodPrefs.size() < 1){
						continue;
					}
					double memberBestPref = memberFoodPrefs.values().stream().findFirst().get();
					if (bestPref < 0 || bestPref > memberBestPref){
						if (!membersOrder.contains(familyMember.getName())){
							bestPref = memberBestPref;
							lowestMember = familyMember.getName();
						}	
					}
				}
				membersOrder.add(lowestMember);
			}
			memberPriorityList.put(meal, membersOrder);
		}
	}

	private MemberName getPriorityMember(List<MemberName> membersOrder){
		return membersOrder.get(0);
	}

	private FoodType getBestFood(MealType meal, MemberName member, Map<MemberName, Map<MealType, Map<FoodType, Double>>> orderedFamilyPreferences){
		return orderedFamilyPreferences.get(member).get(meal).keySet().iterator().next();

	}
	private FoodType getMaximumAvailableMeal(Pantry pantry, MealType mealType) {
		FoodType maximumAvailableMeal = null;
		int maxAvailableMeals = -1;
		for(FoodType foodType : Food.getFoodTypes(mealType)) {
			int numAvailableMeals = pantry.getNumAvailableMeals(foodType);
			if(numAvailableMeals > maxAvailableMeals) {
				maxAvailableMeals = numAvailableMeals;
				maximumAvailableMeal = foodType;
			}
		}
		return maximumAvailableMeal;
	}

	private void resetOptimisticPlanner(List<FamilyMember> familyMembers) {
		for (MealType mealType: MealType.values()) {
			Map<FamilyMember, ArrayList<FoodType>> mealSpecificPlanner = new HashMap<>();
			for (FamilyMember familyMember : familyMembers) {
				mealSpecificPlanner.put(familyMember, new ArrayList<FoodType>());
			}
			optimisticPlanner.put(mealType, mealSpecificPlanner);
		}
	}

	private void addFoodToOrder(ShoppingList shoppingList, FoodType foodType, int quantity) {
		for (int i = 0; i < quantity; i++) {
			shoppingList.addToOrder(foodType);
		}
	}

	private void addFoodToPlanner(Planner planner, FamilyMember member, FoodType foodType) {
		for (Day day: Day.values()) {
			planner.addMeal(day, member.getName(), Food.getMealType(foodType), foodType);
		}
	}

	private void addFoodToPlanner(Planner planner, FamilyMember member, FoodType foodType, ArrayList<Day> days) {
		for (Day day: days) {
			planner.addMeal(day, member.getName(), Food.getMealType(foodType), foodType);
		}
	}

	private void printPantry(Pantry pantry) {
		for (FoodType foodType: FoodType.values()) {
			simPrinter.println(foodType.toString() + ": " + pantry.getNumAvailableMeals(foodType));
		}
	}
}