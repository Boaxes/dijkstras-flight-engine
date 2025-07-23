/** Author: Brandon Connely
 * Date: 7/22/25
 * Purpose: A2
 */
package avl;

public class AVL {

  public Node root;

  private int size;

  public int getSize() {
    return size;
  }

  /** find w in the tree. return the node containing w or
  * null if not found */
  public Node search(String w) {
    return search(root, w);
  }
  private Node search(Node n, String w) {
    if (n == null) {
      return null;
    }
    if (w.equals(n.word)) {
      return n;
    } else if (w.compareTo(n.word) < 0) {
      return search(n.left, w);
    } else {
      return search(n.right, w);
    }
  }

  /** insert w into the tree as a standard BST, ignoring balance */
  public void bstInsert(String w) {
    if (root == null) {
      root = new Node(w);
      size = 1;
      return;
    }
    bstInsert(root, w);
  }

  /* insert w into the tree rooted at n, ignoring balance
   * pre: n is not null */
  private void bstInsert(Node n, String w) {
    int comparisonValue = w.compareTo(n.word);

    if (comparisonValue == 0)
    {
      return;
    }

    if (comparisonValue < 0) { // Base Case: insert to left if empty
      if (n.left == null) {
        n.left = new Node(w, n);
        size++;
      }
      else { // Recursion: go left
        bstInsert(n.left, w);
      }
    }
    else if (comparisonValue > 0) { // Base Case: insert to right if empty
      if (n.right == null) {
        n.right = new Node(w, n);
        size++;
      }
      else { // Recursion: go right
        bstInsert(n.right, w);
      }
    }

  }


  /** insert w into the tree, maintaining AVL balance
  *  precondition: the tree is AVL balanced and any prior insertions have been
  *  performed by this method. */
  public void avlInsert(String w) {
    if (root == null) {
      root = new Node(w);
      size = 1;
    }
    else {
      avlInsert(root, w);
    }
  }


  /* insert w into the tree, maintaining AVL balance
   *  precondition: the tree is AVL balanced and n is not null */
  private void avlInsert(Node n, String w) {
    int compareValue = w.compareTo(n.word);

    if (compareValue == 0) { // Base Case
      return;
    }

    if (compareValue < 0) {
        if (n.left == null) {
        n.left = new Node(w, n);
        size++;
      }
      else {
        avlInsert(n.left, w);
      }
    }
    else {
      if (n.right == null) {
        n.right = new Node(w, n);
        size++;
      }
      else {
        avlInsert(n.right, w);
      }
    }

    rebalance(n);
  }

  /** HELPER METHOD: recalculate the height of node n
   * precondition: n is not null
   * postcondition: n.height is updated based on its children's heights */
  private void recalculateHeight(Node n) {  // Using The Formula Provided in L10B
    int leftHeight;
    if (n.left == null) {
      leftHeight = -1;
    }
    else {
      leftHeight = n.left.height;
    }

    int rightHeight;
    if (n.right == null) {
      rightHeight = -1;
    }
    else {
      rightHeight = n.right.height;
    }

    n.height = 1 + Math.max(leftHeight, rightHeight);
  }

  /** do a left rotation: rotate on the edge from x to its right child.
   *  precondition: x has a non-null right child
   *  postcondition: the subtree rooted at x is rotated left */
  public void leftRotate(Node x) { // Burrowed from textbook psuedocode in L10C
    Node y = x.right;
    x.right = y.left;
    if (y.left != null) {
      y.left.parent = x;
    }

    y.parent = x.parent;
    if (x.parent == null) {
      root = y;
    }
    else if (x == x.parent.left) {
      x.parent.left = y;
    }
    else {
      x.parent.right = y;
    }

    y.left = x;
    x.parent = y;

    recalculateHeight(x);
    recalculateHeight(y);
  }

  /** do a right rotation: rotate on the edge from x to its left child.
   *  precondition: x has a non-null left child
   *  postcondition: the subtree rooted at x is rotated right */
  public void rightRotate(Node x) { // Using The Formula Provided in L10B
    Node y = x.left;
    x.left = y.right;
    if (y.right != null) {
      y.right.parent = x;
    }

    y.parent = x.parent;
    if (x.parent == null) {
      root = y;
    } else if (x == x.parent.left) {
      x.parent.left = y;
    } else {
      x.parent.right = y;
    }

    y.right = x;
    x.parent = y;

    recalculateHeight(x);
    recalculateHeight(y);
  }

  /** HELPER METHOD: Return the balance of a given node.
   * postcondition: returns: Child(Right) Height - Child(Left) Height
   */
  private int getBalance(Node n) {

    int leftHeight = 0;
    if (n.left == null) { // Convention
      leftHeight = -1;
    }
    else {
      leftHeight = n.left.height;
    }

    int rightHeight = 0;
    if (n.right == null) { // Convention
      rightHeight = -1;
    }
    else {
      rightHeight = n.right.height;
    }

    return rightHeight - leftHeight;
  }

  /** rebalance a node N after a potentially AVL-violoting insertion.
  *  precondition: none of n's descendants violates the AVL property */
  public void rebalance(Node n) {
    recalculateHeight(n);
    int balance = getBalance(n);

    if (balance < -1) {
      if (getBalance(n.left) < 0) { // Case 1
        rightRotate(n);
      }
      else { // Case 2
        leftRotate(n.left);
        rightRotate(n);
      }
    } else if (balance > 1) {
      if (getBalance(n.right) < 0) { // Case 3
        rightRotate(n.right);
        leftRotate(n);
      }
      else { // Case 4
        leftRotate(n);
      }
    }
  }

  /** remove the word w from the tree */
  public void remove(String w) {
    remove(root, w);
  }

  /* remove w from the tree rooted at n */
  private void remove(Node n, String w) {
    return; // (enhancement TODO - do the base assignment first)
  }

  /** print a sideways representation of the tree - root at left,
  * right is up, left is down. */
  public void printTree() {
    printSubtree(root, 0);
  }
  private void printSubtree(Node n, int level) {
    if (n == null) {
      return;
    }
    printSubtree(n.right, level + 1);
    for (int i = 0; i < level; i++) {
      System.out.print("        ");
    }
    System.out.println(n);
    printSubtree(n.left, level + 1);
  }

  /** inner class representing a node in the tree. */
  public class Node {
    public String word;
    public Node parent;
    public Node left;
    public Node right;
    public int height;

    public String toString() {
      return word + "(" + height + ")";
    }

    /** constructor: gives default values to all fields */
    public Node() { }

    /** constructor: sets only word */
    public Node(String w) {
      word = w;
    }

    /** constructor: sets word and parent fields */
    public Node(String w, Node p) {
      word = w;
      parent = p;
    }

    /** constructor: sets all fields */
    public Node(String w, Node p, Node l, Node r) {
      word = w;
      parent = p;
      left = l;
      right = r;
    }
  }
}
