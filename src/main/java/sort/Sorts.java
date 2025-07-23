package sort;
/* Author: Brandon Connely
 * Date: 7/1/25
 * Description: A1
 * */


import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Queue;

public class Sorts {

  // maintains a count of comparisons performed by this Sorts object
  private int comparisonCount;

  public int getComparisonCount() {
    return comparisonCount;
  }

  public void resetComparisonCount() {
    comparisonCount = 0;
  }

  /** Sorts A[start..end] in place using insertion sort
   * Precondition: 0 <= start <= end <= A.length */
  public void insertionSort(int[] A, int start, int end) {
    for (int i = start + 1; i < end; i++) { // Loop Invariant: A[start..i-1] is sorted
      int j = i;

      while (j > start && A[j - 1] > A[j]) { // Loop Invariant: all elements satisify: A[start..j-1] <= A[j..i]
        comparisonCount++; // for A[j - 1] > A[j]
        swap(A, j, j - 1);
        j--;
      }
      comparisonCount++; // A[j-1] < A[j] comparison fails case.
    }
  }

  /** Partitions A[start..end] around the pivot A[pivIndex]; returns the
   *  pivot's new index.
   *  Precondition: start <= pivIndex < end
   *  Postcondition: If partition returns i, then
   *  A[start..i] <= A[i] <= A[i+1..end]
   **/
  public int partition(int[] A, int start, int end, int pivIndex) {
    int pivot = A[pivIndex];
    swap(A, pivIndex, start);  // Move pivot to start of subarray

    int j = start + 1;

    for (int i = start + 1; i < end; i++) { // Elements in A[start+1..j-1] are less then(or equal) to the pivot
      if (A[i] <= pivot) {
        comparisonCount++;
        swap(A, i, j);
        j++;
      }
      else {
        comparisonCount++;
      }
    }

    swap(A, start, j - 1);  // Move pivot to its final place
    return j - 1;           // Return the pivot index
  }

  /** use quicksort to sort the subarray A[start..end) */
  public void quickSort(int[] A, int start, int end) {

    if (end - start <= 1) { //Base Case
      return;
    }

    int mid = partition(A, start, end, start);

    quickSort(A, start, mid);     // Recursion(Left Half)
    quickSort(A, mid + 1, end);  //Recursion(RightHalf)
  }

  /** merge the sorted subarrays A[start..mid] and A[mid..end] into
   *  a single sorted array in A. */
  public void merge(int[] A, int start, int mid, int end) {
    int[] B = A.clone();
    int i = start, j = mid, k = start;


    // Main Loop
    while (i < mid && j < end) { // Loop Invariant: A[start..k-1] contains the smallest elements of A[start..mid-1] and A[mid..end-1]
      if (B[i] <= B[j]) {
        comparisonCount++;
        A[k++] = B[i++];
      }
      else {
        comparisonCount++;
        A[k++] = B[j++];
      }
    }

    //Remaining Left Half:
    while (i < mid) {  // Loop Invariant: A[start..k-1] contains all merged elements up to B[i-1] and B[mid..j-1]
      A[k++] = B[i++];
    }

    //Remaining Right Half:
    while (j < end) { // Loop Invariant: A[start..k-1] contains all merged elements up to B[i-1] and B[mid..j-1]
      A[k++] = B[j++];
    }
  }

  /** use mergesort to sort the subarray A[start..end] */
  public void mergeSort(int[] A, int start, int end) {
    if (end - start < 2) {  // Base Case
      return;
    }

    int mid = (start + end) / 2; // Divide

    mergeSort(A, start, mid);    // Recursion[Conquer(left)]
    mergeSort(A, mid, end);      // Recursion[Conquer(right)]

    merge(A, start, mid, end);   // Combine
  }

  /** Sort A using LSD radix sort. */
  public void radixSort(int[] A) {
    if (A.length == 0)
    {
      return;
    }

    int minValue = A[0];
    for (int i = 1; i < A.length; i++) {   // Find the minimum value
      if (A[i] < minValue) {
        minValue = A[i];
      }
    }

    if (minValue < 0) {
      int shift = -minValue;  // Shift all elements to make them non-negative
      for (int i = 0; i < A.length; i++) {
        A[i] += shift;
      }


      radixSortNonNegative(A); // Now this is fine


      for (int i = 0; i < A.length; i++) {
        A[i] -= shift; // Shift elements back
      }
    } else {
      radixSortNonNegative(A);
    }
  }


  /** HELPER METHOD: Sort A using LSD radix sort.
   * Precondition: The Entries of A[] are non-negative. A is not null.
   * */
  private void radixSortNonNegative(int[] A) {

    int maxDigits = getMaxDigits(A); // Find max digit length

    for (int d = 0; d < maxDigits; d++) {  // Sort by digit
      countingSortByDigit(A, d);
    }
  }

  /* return the 10^d's place digit of n */
  private int getDigit(int n, int d) {
    return (n / ((int)Math.pow(10, d))) % 10;
  }
  /** HELPER METHOD: Performs a stable counting sort of A by the digit in the 10^d place.
   *  precondition: 0 <= d < number of digits in max(A)
   *  postcondition: A is sorted based on the 10^d place digit */
  private void countingSortByDigit(int[] A, int d) {
    int n = A.length;
    int[] count = new int[10];  // Digit counts (0–9)
    int[] output = new int[n];  // Output array

    for (int i = 0; i < n; i++) {  // Count occurrences of each digit
      int digit = getDigit(A[i], d);
      count[digit]++;
    }

    int[] start = new int[10];
    start[0] = 0;
    for (int i = 1; i < 10; i++) { // Convert count to starting positions
      start[i] = start[i - 1] + count[i - 1];
    }

    for (int i = 0; i < n; i++) {    // Place elements in output
      int digit = getDigit(A[i], d);
      output[start[digit]] = A[i];
      start[digit]++;
    }

    for (int i = 0; i < n; i++) {   // Copy back to A
      A[i] = output[i];
    }
  }

  /** HELPER METHOD: Finds the number of digits of the largest element in A.
   *  precondition: A is not empty
   *  postcondition: returns the number of digits in max(A) */
  private int getMaxDigits(int[] A) {
    int max = A[0];
    for (int num : A) {
      if (num > max)
      {
        max = num;
      }
    }
    return (int)Math.log10(max) + 1;
  }

  /** swap a[i] and a[j]
   *  pre: 0 <= i, j < a.size
   *  post: values in a[i] and a[j] are swapped */
  public void swap(int[] a, int i, int j) {
    int tmp = a[i];
    a[i] = a[j];
    a[j] = tmp;
  }

}