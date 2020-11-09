package menu.g2;

import java.util.*;

import menu.sim.*;
import menu.sim.Food.FoodType;
import menu.sim.Food.MealType;

public class Player extends menu.sim.Player {

	int size = 0;
	int pantrySize = 0;
	Map<FamilyMember,List<FoodType>> breakfastRanks; // weekly
	Map<FamilyMember,List<FoodType>> lunchRanks;
	List<FoodType> dinnerRanks;
	StorePredictor pred;
	ShoppingList prevList;
	int numBreakfasts = 0;
	int numLunches = 0;
	int numDinners = 0;

	/**
	 * Player constructor
	 *
	 * @param weeks            number of weeks
	 * @param numFamilyMembers number of family members
	 * @param capacity         pantry capacity
	 * @param seed             random seed
	 * @param simPrinter       simulation printer
	 *
	 */
	public Player(Integer weeks, Integer numFamilyMembers, Integer capacity, Integer seed, SimPrinter simPrinter) {
		super(weeks, numFamilyMembers, capacity, seed, simPrinter);

	}

	// option 1:
	// to calculate static ideal pantry:
	// 1.determine spread of how many dinners/lunches/breakfasts we should have
	// based on preferences
	// 2.rank breakfast, lunch, and dinner foods based on how much they are
	// generally liked
	// 3.calculate ideal shopping list based on this

	// how we're gonna order in the future
	// 4.order difference between current pantry and ideal list

	// option 2:
	// expected value
	// simulate pantry being used
	// simulate an expected value
	// search space, choose the ideal pantry

	// rankings based on history
	// longterm: tree of rankings, tree static
	// daily rankings for lunch, dinner

	/**
	 * Create shopping list of meals to stock pantry
	 *
	 * @param week          current week
	 * @param numEmptySlots number of empty slots left in the pantry
	 * @param familyMembers all family members
	 * @param pantry        pantry inventory of remaining foods
	 * @param mealHistory   history of previous meal allocations
	 * @return shopping list of foods to order
	 *
	 */
	public ShoppingList stockPantry(Integer week, Integer numEmptySlots, List<FamilyMember> familyMembers,
			Pantry pantry, MealHistory mealHistory) {

		// 1.) calculate how many breakfast, lunch, and dinner items we want based on
		// preferences
		this.size = familyMembers.size();
		if(week == 1){
			this.pantrySize = pantry.getNumEmptySlots();
			this.pred = new StorePredictor();

		}
		List<Integer> cutoffs = calcFreqMeals(pantry, this.size, familyMembers);
		numBreakfasts = cutoffs.get(0);
		numLunches = cutoffs.get(1);
		numDinners = cutoffs.get(2);
	//	System.out.println(numBreakfasts+ " " + numLunches + " " + numDinners);
		// 2.) rank breakfast, lunch, and dinner items
		breakfastRanks = calcOrderRanksBreakfast(familyMembers);
		lunchRanks = calcOrderRanksLunch(familyMembers);
		dinnerRanks = calcOrderRanksDinner(familyMembers);
		this.pred.setPreviousPantry(pantry);
		this.prevList= calcShoppingList(pantry, mealHistory, familyMembers);
		this.pred.calculateProbabilities(this.prevList, week);
		return this.prevList;
		// TODO: Make these smart allocations
		/*
		 * int numBreakfastFoods = random.nextInt(numEmptySlots + 1); int numLunchFoods
		 * = random.nextInt(numEmptySlots - numBreakfastFoods + 1); int numDinnerFoods =
		 * numEmptySlots - numBreakfastFoods - numLunchFoods;
		 * 
		 * 
		 * int numBreakfastFoods = random.nextInt(numEmptySlots + 1); int numLunchFoods
		 * = random.nextInt(numEmptySlots - numBreakfastFoods + 1); int numDinnerFoods =
		 * numEmptySlots - numBreakfastFoods - numLunchFoods;
		 * 
		 * 
		 * ShoppingList shoppingList = new ShoppingList();
		 * shoppingList.addLimit(MealType.BREAKFAST, numBreakfastFoods);
		 * shoppingList.addLimit(MealType.LUNCH, numLunchFoods);
		 * shoppingList.addLimit(MealType.DINNER, numDinnerFoods);
		 * 
		 * List<FoodType> breakfastFoods = Food.getFoodTypes(MealType.BREAKFAST);
		 * List<FoodType> lunchFoods = Food.getFoodTypes(MealType.LUNCH); List<FoodType>
		 * dinnerFoods = Food.getFoodTypes(MealType.DINNER);
		 * 
		 * for(int i = 0; i < 2 * capacity; i++)
		 * shoppingList.addToOrder(MealType.BREAKFAST,
		 * breakfastFoods.get(random.nextInt(breakfastFoods.size()))); for(int i = 0; i
		 * < 2 * capacity; i++) shoppingList.addToOrder(MealType.LUNCH,
		 * lunchFoods.get(random.nextInt(lunchFoods.size()))); for(int i = 0; i < 2 *
		 * capacity; i++) shoppingList.addToOrder(MealType.DINNER,
		 * dinnerFoods.get(random.nextInt(dinnerFoods.size())));
		 * 
		 * if(Player.hasValidShoppingList(shoppingList, numEmptySlots)) return
		 * shoppingList;
		 * 
		 * return new ShoppingList();
		 */
	}


	private ShoppingList calcShoppingList(Pantry pantry, MealHistory mealHistory, List<FamilyMember> familyMembers) {
		//how many breakfast items
		List<FoodType> breakfasts = calcBreakfast(pantry, mealHistory);
	//	System.out.println("BREAKFASTS " + breakfasts.toString());
		//how many lunch items
		List<FoodType> lunches = calcLunch(pantry, mealHistory);
	//	System.out.println("LUNCH " + lunches.toString());
		//how many dinner items
		List<FoodType> dinners = calcDinner(pantry, mealHistory, familyMembers);
	//	System.out.println("DINNER " + dinners.toString());

		//combine lists?
		return combineShoppingLists(breakfasts, lunches, dinners, pantry);
	}

	// Aum
	// TODO - test to make sure it totals to the actual pantry size
	// 1.) calculate how many breakfast, lunch, and dinner items we want based on
	// preferences
	List<Integer> calcFreqMeals(Pantry pantry, int size, List<FamilyMember> familyMembers) {
		int breakfast = 7*familyMembers.size();
		int lunch = 7*familyMembers.size();
		int dinner = 7*familyMembers.size();

		double meanBreakfastSatisfaction = 0.0;
		double meanLunchSatisfaction = 0.0;
		double meanDinnerSatisfaction = 0.0;
		for(FamilyMember member: familyMembers){
			Map<FoodType, Double> foodPreferenceMap = member.getFoodPreferenceMap();
			for(Food.FoodType key: foodPreferenceMap.keySet()){
				Food f = new Food();
				if(f.getMealType(key) == MealType.BREAKFAST){
					meanBreakfastSatisfaction += foodPreferenceMap.get(key);
				} else if(f.getMealType(key) == MealType.LUNCH){
					meanLunchSatisfaction += foodPreferenceMap.get(key);
				} else {
					meanDinnerSatisfaction += foodPreferenceMap.get(key);
				}
			}
		}
		meanBreakfastSatisfaction = meanBreakfastSatisfaction/(10.0 * (double)familyMembers.size());
		meanLunchSatisfaction = meanLunchSatisfaction/(10.0 * (double)familyMembers.size());
		meanDinnerSatisfaction = meanDinnerSatisfaction/(20.0 * (double)familyMembers.size());
		int pantrySizeNew = pantrySize - breakfast - lunch - dinner;
		int extraBreakfastAllocation = (int) ((pantrySizeNew-breakfast)*(meanBreakfastSatisfaction/(meanBreakfastSatisfaction+meanLunchSatisfaction+meanDinnerSatisfaction)));
		int extraLunchAllocation = (int) ((pantrySizeNew-lunch)*(meanLunchSatisfaction/(meanBreakfastSatisfaction+meanLunchSatisfaction+meanDinnerSatisfaction)));
		int extraDinnerAllocation = (int) ((pantrySizeNew-dinner)*(meanDinnerSatisfaction/(meanBreakfastSatisfaction+meanLunchSatisfaction+meanDinnerSatisfaction)));
	//	System.out.println(extraBreakfastAllocation);
		breakfast+= extraBreakfastAllocation;
		lunch += extraLunchAllocation;
		dinner += extraDinnerAllocation;

		breakfast -= pantry.getNumAvailableMeals(MealType.BREAKFAST);
		lunch -= pantry.getNumAvailableMeals(MealType.LUNCH);
		dinner -= pantry.getNumAvailableMeals(MealType.DINNER);
		ArrayList<Integer> cutoffs = new ArrayList<Integer>();
		cutoffs.add(breakfast);
		cutoffs.add(lunch);
		cutoffs.add(dinner);
		
		return cutoffs;
	}

	// Ahad - Done by Aum
	// Ahad
	// TODO
	// 2.) rank breakfast items to maximum each person's satisfaction 
	// List<FoodType> calcOrderRanksBreakfast(List<FamilyMember> familyMembers) {
	// 	Food f = new Food();
	// 	List<FoodType> allBreakfasts = f.getFoodTypes(MealType.BREAKFAST);
	// 	Map<FoodType, Double> lowestPerson = new HashMap<>();
	// 	for(FoodType food: allBreakfasts){
	// 		double lowest = 1.1;
	// 		for(FamilyMember member: familyMembers){
	// 			lowest = Math.min (member.getFoodPreference(food), lowest);
	// 		}
	// 		lowestPerson.put(food, lowest);
	// 	}
	// 	return sortByValue(lowestPerson); //return right value
	// }


	Map<FamilyMember, List<FoodType>> calcOrderRanksBreakfast(List<FamilyMember> familyMembers) {

		Map<FamilyMember, List<FoodType>> familyBreakfastRankings = new HashMap<>();
		
		for(FamilyMember member: familyMembers){
			familyBreakfastRankings.put(member, calcOrderRanksBreakfastForEachPersonHelper(member));
		}

		return familyBreakfastRankings;
		

	}




	//method for individual choices
	List<FoodType> calcOrderRanksBreakfastForEachPersonHelper(FamilyMember familyMember) {
		Food f = new Food();
		List<FoodType> allBreakfasts = f.getFoodTypes(MealType.BREAKFAST);
		Map<FoodType, Double> lowestPerson = new HashMap<>();
		for(FoodType food: allBreakfasts){
			double lowest = 1.0;
			lowest = familyMember.getFoodPreference(food);
			
			lowestPerson.put(food, lowest);
		}
		return sortByValue(lowestPerson); //return right value
	}



	// Ahad - Done by Aum
	// Ahad
	// TODO on Wednesday - take into account the repeat penalty
	// 2.) rank lunch items like breakfast
	// List<FoodType> calcOrderRanksLunch(List<FamilyMember> familyMembers) {
	// 	Food f = new Food();
	// 	List<FoodType> allBreakfasts = f.getFoodTypes(MealType.LUNCH);
	// 	Map<FoodType, Double> lowestPerson = new HashMap<>();
	// 	for(FoodType food: allBreakfasts){
	// 		double lowest = 1.1;
	// 		for(FamilyMember member: familyMembers){
	// 			lowest = Math.min (member.getFoodPreference(food), lowest);
	// 		}
	// 		lowestPerson.put(food, lowest);
	// 	}
	// 	return sortByValue(lowestPerson);
	// }

	Map<FamilyMember, List<FoodType>> calcOrderRanksLunch(List<FamilyMember> familyMembers) {

		Map<FamilyMember, List<FoodType>> familyLunchRankings = new HashMap<>();

		for(FamilyMember member: familyMembers){
			familyLunchRankings.put(member, calcOrderRanksLunchForEachHelper(member));
		}

		return familyLunchRankings;


	}



	List<FoodType> calcOrderRanksLunchForEachHelper(FamilyMember familyMember) {
		Food f = new Food();
		List<FoodType> allBreakfasts = f.getFoodTypes(MealType.LUNCH);
		Map<FoodType, Double> lowestPerson = new HashMap<>();
		for(FoodType food: allBreakfasts){
			double lowest = 1.0;
			lowest = familyMember.getFoodPreference(food);
			lowestPerson.put(food, lowest);
		}
		return sortByValue(lowestPerson);
	}

	// Ahad - Done by Aum
	// TODO wednesday - weighting depending on if people are least satisfied
	// 2.) rank dinner items based on the mean satisfaction of all the members (bring up the floor of everyone's satisfaction)
	List<FoodType> calcOrderRanksDinner(List<FamilyMember> familyMembers) {
		Food f = new Food();
		List<FoodType> allBreakfasts = f.getFoodTypes(MealType.DINNER);
		Map<FoodType, Double> lowestPerson = new HashMap<>();
		double satisfactionSum = 0;
		double count = 0;
		for(FoodType food: allBreakfasts){
			double lowest = 1.1;
			for(FamilyMember member: familyMembers){
				// lowest = Math.min (member.getFoodPreference(food), lowest);
				satisfactionSum += member.getFoodPreference(food);
				count += 1;
				satisfactionSum = satisfactionSum/count;

			}
			// lowestPerson.put(food, lowest);
			lowestPerson.put(food, satisfactionSum);
		}
		return sortByValue(lowestPerson);
	}

	public static List<FoodType> sortByValue(Map<FoodType, Double> hm) 
    { 
        // Create a list from elements of HashMap 
        List<Map.Entry<FoodType, Double> > list = 
               new LinkedList<Map.Entry<FoodType, Double> >(hm.entrySet()); 
  
        // Sort the list 
        Collections.sort(list, new Comparator<Map.Entry<FoodType, Double> >() { 
            public int compare(Map.Entry<FoodType, Double> o1,  
                               Map.Entry<FoodType, Double> o2) 
            { 
                return (o2.getValue()).compareTo(o1.getValue()); 
            } 
        }); 
          
        // put data from sorted list to hashmap  
        HashMap<FoodType, Double> temp = new LinkedHashMap<FoodType, Double>(); 
        for (Map.Entry<FoodType, Double> aa : list) { 
            temp.put(aa.getKey(), aa.getValue()); 
        } 
        return new ArrayList(temp.keySet()); 
    } 

	//SCOTT
	//TODO
	//determine frequency for breakfast items
	//based on cutoffs, what is already in the pantry, rankings, put higher priority in the shopping list on the people that are least satisfied
	List<FoodType> calcBreakfast(Pantry pantry, MealHistory mealHistory) {
		//numBreakfasts
		int difference = numBreakfasts -  pantry.getNumAvailableMeals(MealType.BREAKFAST);

		Map<MealType, Map<FoodType, Integer>> map = pantry.getMealsMap();

		List<FoodType> breakfasts = new ArrayList<>();

		//could have a map of food -> frequency, order the difference

		List<FoodType> topFoods = new ArrayList<>();
		//topFoods.add(breakfastRanks.get(0));
		//topFoods.add(breakfastRanks.get(1));
		//topFoods.add(breakfastRanks.get(2));

		/*for(int i = 0; i < 10; i++) {
			topFoods.add(breakfastRanks.get(i));
		}

		topFoods.sort((food1, food2) -> (int) (pred.probs.get(food1)*100) - (int) (pred.probs.get(food2)*100));

		for(FoodType food : topFoods) {
			System.out.println("food is " + food);
			System.out.println("prob is " + pred.probs.get(food));
		}*/

		List<FamilyMember> familyMembers = new ArrayList<>(breakfastRanks.keySet());
		familyMembers.sort((member1, member2) -> (int) (100*member1.getSatisfaction()) - (int) (100*member2.getSatisfaction()));

		FamilyMember leastSatisfied = familyMembers.get(0);

		for(FamilyMember familyMember : familyMembers) {
			System.out.println("satisfaction is " + familyMember.getSatisfaction());
		}


		//target top 3 breakfasts
		for(int i = 0; i < 3; i++) {
			FoodType topFood = breakfastRanks.get(leastSatisfied).get(i);
			int freqTop = map.get(MealType.BREAKFAST).get(topFood);

			breakfasts = addFoods(breakfasts, topFood, (int) (difference/3*1.5));
		}

	/*	for(int i = 4; i < breakfastRanks.size(); i++) {
			FoodType badFood = breakfastRanks.get(i);
			int freqBad = map.get(MealType.BREAKFAST).get(badFood);
			breakfasts = addFoods(breakfasts, badFood, (int) (difference/5*1.5));
		} */

		//System.out.println(breakfasts);
		return breakfasts;
	}


	//SCOTT
	//TODO
	//determine frequency for lunch items
	//based on cutoffs, what is already in the pantry, rankings, put higher priority in the shopping list on the people that are least satisfied
	List<FoodType> calcLunch(Pantry pantry, MealHistory mealHistory) {
		int difference = numLunches -  pantry.getNumAvailableMeals(MealType.LUNCH);

		Map<MealType, Map<FoodType, Integer>> map = pantry.getMealsMap();

		List<FoodType> lunches = new ArrayList<>();

		List<FamilyMember> familyMembers = new ArrayList<>(breakfastRanks.keySet());
		familyMembers.sort((member1, member2) -> (int) (100*member1.getSatisfaction()) - (int) (100*member2.getSatisfaction()));

		FamilyMember leastSatisfied = familyMembers.get(0);

		//target top 5 top lunches
		for(int i = 0; i < 3; i++) {
			FoodType topFood = lunchRanks.get(leastSatisfied).get(i);
			int freqTop = map.get(MealType.LUNCH).get(topFood);

			lunches = addFoods(lunches, topFood, (int) (difference/3*1.3));
		}

	/*	for(int i = 6; i < lunchRanks.size(); i++) {
			FoodType badFood = lunchRanks.get(i);
			int freqBad = map.get(MealType.LUNCH).get(badFood);

			lunches = addFoods(lunches, badFood, (int) (difference/7*1.2));
		} */

		//System.out.println(lunches);
		return lunches;
		
	}

	//SCOTT
	//TODO
	//determine frequency for dinner items
	//based on cutoffs, what is already in the pantry, rankings, put higher priority in the shopping list on the food that brings up the floor
	List<FoodType> calcDinner(Pantry pantry, MealHistory mealHistory, List<FamilyMember> familyMembers) {
		int numFamMembers = familyMembers.size();
		int difference = numDinners -  pantry.getNumAvailableMeals(MealType.DINNER);
	//	System.out.println("existing meals " + pantry.getNumAvailableMeals(MealType.DINNER));
	//	System.out.println("difference is " + difference);
	//	System.out.println("size is " + size);
		Map<MealType, Map<FoodType, Integer>> map = pantry.getMealsMap();

		List<FoodType> dinners = new ArrayList<>();

		//target top 5 top dinners
		for(int i = 0; i < 3; i++) {
			FoodType topFood = dinnerRanks.get(i);
			int freqTop = map.get(MealType.DINNER).get(topFood);



			dinners = addFoods(dinners, topFood, (int) (difference/3*1.6));
		}
	//	System.out.println(dinners.size());
	/*	for(int i = 6; i < dinnerRanks.size(); i++) {
			FoodType badFood = dinnerRanks.get(i);
			int freqBad = map.get(MealType.DINNER).get(badFood);

			dinners = addFoods(dinners, badFood, (int) (difference/7*1.2));
		} */

		//System.out.println(dinners);
		return dinners;
		
	}

	List<FoodType> addFoods(List<FoodType> li, FoodType food, int num) {
		List<FoodType> foods = new ArrayList<>(li);
		for(int i = 0; i < num; i++) {
			foods.add(food);
		}
		return foods;
	}

	//SCOTT
	//TODO
	//combine breakfast, lunch, and dinner shopping lists into one
	//take into account cutoffs -> generate shopping list
	//backup ones total to 7*2 or 7*3 of everything (pantry size/n)
	ShoppingList combineShoppingLists(List<FoodType> breakfasts, List<FoodType> lunches, List<FoodType> dinners, Pantry pantry) {
		ShoppingList shoppingList = new ShoppingList();

		//add cutoffs for how many of each meal type
    	shoppingList.addLimit(MealType.BREAKFAST, numBreakfasts -  pantry.getNumAvailableMeals(MealType.BREAKFAST));
    	shoppingList.addLimit(MealType.LUNCH, numLunches - pantry.getNumAvailableMeals(MealType.LUNCH));
		shoppingList.addLimit(MealType.DINNER, numDinners - pantry.getNumAvailableMeals(MealType.DINNER));
		
		//add all breakfast foods
		for(FoodType breakfast : breakfasts) {
			shoppingList.addToOrder(breakfast);
		}

		//add all lunch foods
		for(FoodType lunch : lunches) {
			shoppingList.addToOrder(lunch);
		}

		//add all dinner foods
		for(FoodType dinner : dinners) {
			shoppingList.addToOrder(dinner);
		}

		return shoppingList;
	}

	/**
	 * Plan meals
	 *
	 * @param week          current week
	 * @param familyMembers all family members
	 * @param pantry        pantry inventory of remaining foods
	 * @param mealHistory   history of previous meal allocations
	 * @return planner of assigned meals for the week
	 *
	 */

	// TODOs
	// AUM

	public Planner planMeals(Integer week, List<FamilyMember> familyMembers, Pantry pantry, MealHistory mealHistory) {

		// 1. randomly choose between top three meals only in lunch and dinner
		// get max available or second max available, remove from inventory, add to
		// planner
		this.pred.setCurrentPantry(pantry);
		List<MemberName> memberNames = new ArrayList<>();
		for (FamilyMember familyMember : familyMembers)
			memberNames.add(familyMember.getName());

		Pantry originalPantry = pantry.clone();
	//	System.out.println(pantry.getMealsMap().toString());
		Planner planner = new Planner(memberNames);
		for (MemberName memberName : memberNames) {
			for (Day day : Day.values()) {
				List<FoodType> maxAvailableBreakfastMeal = getMaximumAvailableMeal(pantry, MealType.BREAKFAST, familyMembers);
				int satisfied = 0;
				int left = familyMembers.size();
				for(FoodType food: maxAvailableBreakfastMeal){
					if(pantry.getNumAvailableMeals(food) < left){
						left -= pantry.getNumAvailableMeals(food);
						int i = pantry.getNumAvailableMeals(food);
						for(int j = satisfied; j < pantry.getNumAvailableMeals(food) + satisfied; j++){
							planner.addMeal(day, familyMembers.get(j).getName(), MealType.BREAKFAST, food);
							pantry.removeMealFromInventory(food);
						}
						satisfied += i;
					} else {
						for(int i = satisfied ; i < familyMembers.size(); i++){
							planner.addMeal(day, familyMembers.get(i).getName(), MealType.BREAKFAST, food);
							pantry.removeMealFromInventory(food);
						}
						break;
					}
				}
				
				List<FoodType> maxAvailableLunchMeal = new ArrayList<>();
				maxAvailableLunchMeal= getMaximumAvailableMealLunch(pantry, MealType.LUNCH, familyMembers);//this.dinnerRanks.get(getRandomAvailableMeal());
			    satisfied = 0;
				left = familyMembers.size();
				for(FoodType food: maxAvailableLunchMeal){
					if(pantry.getNumAvailableMeals(food) < left && pantry.getNumAvailableMeals(food) > 0){
						left -= pantry.getNumAvailableMeals(food);
						int i = pantry.getNumAvailableMeals(food);
					//	System.out.println(i);
						for(int j = satisfied; j < pantry.getNumAvailableMeals(food) + satisfied; j++){
							planner.addMeal(day, familyMembers.get(j).getName(), MealType.LUNCH, food);
							pantry.removeMealFromInventory(food);
						}
						satisfied += i;
					} else if (pantry.getNumAvailableMeals(food) > 0) {
						for(int i = satisfied ; i < familyMembers.size(); i++){
							planner.addMeal(day, familyMembers.get(i).getName(), MealType.LUNCH, food);
							pantry.removeMealFromInventory(food);
						}
						break;
					}
				}
			//	System.out.println(planner.getPlan().toString());

				// if (pantry.getNumAvailableMeals(maxAvailableLunchMeal) > 0) {
				// 	planner.addMeal(day, memberName, MealType.LUNCH, maxAvailableLunchMeal);
				// 	pantry.removeMealFromInventory(maxAvailableLunchMeal);
				// }
			}
		}

		for (Day day : Day.values()) {
			FoodType maxAvailableDinnerMeal =  getMaximumAvailableMealDinner(pantry, MealType.DINNER, familyMembers); //this.dinnerRanks.get(getRandomAvailableMeal());
		//	System.out.println(pantry.getNumAvailableMeals(maxAvailableDinnerMeal));
			Integer numDinners = Math.min(pantry.getNumAvailableMeals(maxAvailableDinnerMeal), familyMembers.size());
			for (int i = 0; i < numDinners; i++) {
				MemberName memberName = memberNames.get(i);
				planner.addMeal(day, memberName, MealType.DINNER, maxAvailableDinnerMeal);
				pantry.removeMealFromInventory(maxAvailableDinnerMeal);
			}
		}

		if (Player.hasValidPlanner(planner, originalPantry))
			return planner;
		return new Planner();
	}

	private FoodType getRandomAvailableMeal(Pantry pantry, MealType mealType){
	 /*	FoodType maximumAvailableMeal = null;
    	int maxAvailableMeals = -1;
    	for(FoodType foodType : Food.getFoodTypes(mealType)) {
    		int numAvailableMeals = pantry.getNumAvailableMeals(foodType);
    		if(numAvailableMeals > maxAvailableMeals) {
    			maxAvailableMeals = numAvailableMeals;
    			maximumAvailableMeal = foodType;
    		}
    	}
    	return maximumAvailableMeal; */
		Random rand = new Random();
		int randomNum = rand.nextInt(3);
		if(mealType == MealType.LUNCH){
			return lunchRanks.get(randomNum);
		} else {
			return dinnerRanks.get(randomNum);
		}
	}

	private List<FoodType> getMaximumAvailableMealLunch(Pantry pantry, MealType mealType, List<FamilyMember> members){
		int i =  0;
		int quantity = members.size();
		ArrayList<FoodType> foo =  new ArrayList<FoodType> ();
		while( i < this.lunchRanks.size()){
			if(pantry.getNumAvailableMeals(this.lunchRanks.get(i))>= quantity){
				foo.add(this.lunchRanks.get(i));
				return foo;
			} else {
				foo.add(this.lunchRanks.get(i));
				quantity -= pantry.getNumAvailableMeals(this.lunchRanks.get(i));
			}
			i++;
		}
    	return foo;
/*		FoodType maximumAvailableMeal = null;
		int maxAvailableMeals = 21;
		for (FoodType foodType : Food.getFoodTypes(mealType)) {
			int ranking = this.breakfastRanks.indexOf(foodType);
			if (maxAvailableMeals > ranking) {
				maxAvailableMeals = ranking;
				maximumAvailableMeal = foodType;
			}
		}
		return maximumAvailableMeal; */
	}

	private FoodType getMaximumAvailableMealDinner(Pantry pantry, MealType mealType, List<FamilyMember> members){
		int i =  0;
		int quantity = members.size();
		int maxQuantity = 0;
		FoodType max = FoodType.DINNER1;
		while( i < this.dinnerRanks.size()){
			if(pantry.getNumAvailableMeals(this.dinnerRanks.get(i))>= quantity){
				return this.dinnerRanks.get(i);
			} else if(pantry.getNumAvailableMeals(this.dinnerRanks.get(i))>= maxQuantity){
				maxQuantity = pantry.getNumAvailableMeals(this.dinnerRanks.get(i));
				max = this.dinnerRanks.get(i);
			}
			i++;
		}
    	return max;
/*		FoodType maximumAvailableMeal = null;
		int maxAvailableMeals = 21;
		for (FoodType foodType : Food.getFoodTypes(mealType)) {
			int ranking = this.breakfastRanks.indexOf(foodType);
			if (maxAvailableMeals > ranking) {
				maxAvailableMeals = ranking;
				maximumAvailableMeal = foodType;
			}
		}
		return maximumAvailableMeal; */
	}

	private List<FoodType> getMaximumAvailableMeal(Pantry pantry, MealType mealType, List<FamilyMember> members){
		FoodType maximumAvailableMeal = FoodType.BREAKFAST1;
		int i =  0;
		int quantity = members.size();
		ArrayList<FoodType> foo =  new ArrayList<FoodType> ();
		while( i < this.breakfastRanks.size()){
			if(pantry.getNumAvailableMeals(this.breakfastRanks.get(i))>= quantity){
				foo.add(this.breakfastRanks.get(i));
				return foo;
			} else {
				foo.add(this.breakfastRanks.get(i));
				quantity -= pantry.getNumAvailableMeals(this.breakfastRanks.get(i));
			}
			i++;
		}
    	return foo;
/*		FoodType maximumAvailableMeal = null;
		int maxAvailableMeals = 21;
		for (FoodType foodType : Food.getFoodTypes(mealType)) {
			int ranking = this.breakfastRanks.indexOf(foodType);
			if (maxAvailableMeals > ranking) {
				maxAvailableMeals = ranking;
				maximumAvailableMeal = foodType;
			}
		}
		return maximumAvailableMeal; */
	}
}