package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.ProductRepository;
import com.ecommerce.project.service.CartService;
import com.ecommerce.project.service.FileService;
import com.ecommerce.project.service.ProductSemanticSearchService;
import com.ecommerce.project.service.ProductService;
import com.ecommerce.project.util.AuthUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;
    private final FileService fileService;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final ProductSemanticSearchService productSemanticSearchService;

    private final AuthUtil authUtil;
    @Value("${project.image}")
    private String path;

    @Value("${image.base.url}")
    private String imageBaseUrl;

    @Value("${app.search.semantic.top-k:20}")
    private int semanticTopK;



    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public ProductDTO addProduct(Long categoryId, ProductDTO productDTO) {


        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));

        boolean isProductNotPresent = true;

        List<Product> products = category.getProducts();
        for (Product value : products) {
            if (value.getProductName().equals(productDTO.getProductName())) {
                isProductNotPresent = false;
                break;
            }
        }

        // Check if product already present or not
        if (isProductNotPresent) {
            //automatically converts (maps) the productDTO object into a Product object.
            Product product = modelMapper.map(productDTO, Product.class);
            product.setImage("cal.png");
            product.setTags(productDTO.getTags());
            product.setCategory(category);
            product.setUser(authUtil.loggedInUser());
            double specialPrice = product.getPrice() -
                    ((product.getDiscount() * 0.01) * product.getPrice());
            product.setSpecialPrice(specialPrice);
            Product savedProduct = productRepository.save(product);
            return modelMapper.map(savedProduct, ProductDTO.class);
        } else throw new APIException("Product already exists !!!");
    }


    private String constructImageUrl(String imageName) {
        return imageBaseUrl.endsWith("/") ? imageBaseUrl + imageName : imageBaseUrl + "/" + imageName;
    }

    @Override
    @Cacheable(
            value = "publicProducts",
            key = "#pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder + '/' + (#keyword == null ?'':#keyword.toLowerCase()) + '/' + (#category == null ? '': #category.toLowerCase())"
    )
    public ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String keyword, String category) {

        //Implement pagination
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if(keyword !=null && !keyword.isEmpty()) {
            spec = spec.and((root, query, cb) -> {
                String likeKeyword = "%" +keyword.toLowerCase() + "%";
                return cb.or(
                        cb.like(cb.lower(root.get("productName")),likeKeyword),
                        cb.like(cb.lower(root.get("description")),likeKeyword),
                        cb.like(cb.lower(root.get("tags")),likeKeyword)
                );
            });
        }

        if(category !=null && !category.isEmpty()) {
            spec = spec.and((root,query,cb) -> cb.like(root.get("category").get("categoryName"), category));
        }



        Page<Product> pageProducts = productRepository.findAll(spec,pageDetails);


        // check if product size is 0 or not
        List<Product> products = pageProducts.getContent();

        // Transformation of the list of products into product response
        List<ProductDTO> productDTOs = products.stream()
                .map(product -> {
                  ProductDTO productDTO = modelMapper.map(product, ProductDTO.class);
                  productDTO.setImage(constructImageUrl(product.getImage()));
                  return productDTO;
                        }).toList();
        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOs);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }



    @Override
    public ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        //Implement pagination
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findAll(pageDetails);

        return buildProductResponse(pageProducts);
    }

    @Override
    public ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        User user = authUtil.loggedInUser();
        Page<Product> pageProducts = productRepository.findByUser(user,pageDetails);



        return buildProductResponse(pageProducts);
    }

    @Override
    @Cacheable(
            value = "productSearch",
            key = " 'search/' + (#query == null ? '' : #query.toLowerCase()) + '/' + #semantic + '/' + #pageNumber + '/' + #pageSize + '/' + #sortBy + '/' + #sortOrder"
    )
    public ProductResponse searchProducts(String query, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, Boolean semantic) {
        List<String> terms = parseSearchTerms(query);
        Sort sortByAndOrder = buildProductSort(sortBy,sortOrder);
        List<Product> classicProducts = productRepository.findAll(buildClassicSearchSpec(terms),sortByAndOrder);
        boolean hasCommaSeparatedTerms = query !=null && query.contains(",");
        boolean shouldUseSemanticSearch = semantic && productSemanticSearchService.isEnabled() && query !=null && !query.isBlank();

        if(!shouldUseSemanticSearch){
            return buildProductResponse(classicProducts,pageNumber,pageSize);
        }

        int safePageNumber = pageNumber == null ? 0 : Math.max(pageNumber,0);
        int safePageSize   = pageSize == null ? 10: Math.max(pageSize,1);
        int semanticLimit = Math.max(semanticTopK,(safePageNumber +1) *safePageSize);
        List<Long> semanticProductIds = productSemanticSearchService.searchProductIds(query,semanticLimit);
        List<Product> semanticProducts = findProductsByOrderedIds(semanticProductIds);
        List<Product> products = hasCommaSeparatedTerms
                ? mergePrioritizedProducts(classicProducts,semanticProducts)
                : mergePrioritizedProducts(semanticProducts,classicProducts);

        return buildProductResponse(products,pageNumber,pageSize);
    }


    @Override
    public int reindexProductSearch() {
        List<Product> products = productRepository.findAll();
        products.forEach(productSemanticSearchService::indexProduct);

        return products.size();
    }


    @Override
    public ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        // check if product size is 0 or not
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Category", "categoryId", categoryId));


        //Implement pagination
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByCategoryOrderByPriceAsc(category, pageDetails);


        // check if product size is 0 or not
        List<Product> products = pageProducts.getContent();

        if (products.isEmpty()) {
            throw new APIException(category.getCategoryName() + " category does not have any products");
        }

        // Transformation of the list of products into product response
        List<ProductDTO> productDTOs = products.stream()
                .map(product -> modelMapper.map(product, ProductDTO.class))
                .collect(Collectors.toList());

        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOs);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;

    }

    @Override
    public ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {

        //Implement pagination
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Product> pageProducts = productRepository.findByProductNameLikeIgnoreCase('%' + keyword + '%', pageDetails);


        // check if product size is 0 or not
        List<Product> products = pageProducts.getContent();
        // Transformation of the list of products into product response
        List<ProductDTO> productDTOs = products.stream()
                .map(product -> modelMapper.map(product, ProductDTO.class))
                .collect(Collectors.toList());

        if (products.isEmpty()) {
            throw new APIException("Products not found with keyword " + keyword);
        }

        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOs);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    @Override
    public ProductDTO updateProduct(Long productId, ProductDTO productDTO) {

        // Get the existing product from DB
        Product productFromDB = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));
        // Update the product info with the one in the request body
        //automatically converts (maps) the productDTO object into a Product object.
        Product product = modelMapper.map(productDTO, Product.class);
        productFromDB.setProductName(product.getProductName());
        productFromDB.setDescription(product.getDescription());
        productFromDB.setQuantity(product.getQuantity());
        productFromDB.setDiscount(product.getDiscount());
        productFromDB.setPrice(product.getPrice());
        productFromDB.setSpecialPrice(product.getSpecialPrice());
        productFromDB.setTags(product.getTags());


        // Save to database
        Product savedProduct = productRepository.save(productFromDB);

        List<Cart> carts = cartRepository.findCartsByProductId(productId);

        List<CartDTO> cartDTOS = carts.stream().map(cart -> {
            CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
            List<ProductDTO> products = cart.getCartItems().stream()
                    .map(p -> modelMapper.map(p.getProduct(), ProductDTO.class))
                    .collect(Collectors.toList());
            cartDTO.setProducts(products);
            return cartDTO;
        }).collect(Collectors.toList());

        cartDTOS.forEach(cart -> cartService.updateProductsInCarts(cart.getCartId(),productId));
        return mapProductToDTO(savedProduct);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public ProductDTO deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));


        List<Cart> carts = cartRepository.findCartsByProductId(productId);
        carts.forEach(cart -> cartService.deleteProductFromCart(cart.getCartId(),productId));
        
        productRepository.delete(product);
        return modelMapper.map(product, ProductDTO.class);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public ProductDTO updateProductImage(Long productId, MultipartFile image) throws IOException {

        // Get product from DB
        Product productFromDb = productRepository.findById(productId).
                orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        // Upload image to server
        // Get the file name of upload image
        String fileName = fileService.uploadImage(path, image);
        // Updating the new file name to the product
        productFromDb.setImage(fileName);

        // Save updated product
        Product updatedProduct = productRepository.save(productFromDb);
        // return DTO after mapping product to DTO
        return modelMapper.map(updatedProduct, ProductDTO.class);
    }

    private List<String> parseSearchTerms(String query){
        if(query==null || query.isBlank()){
            return List.of();
        }

        return Stream.of(query.split(","))
                .map(String::trim)
                .filter(term->!term.isBlank())
                .toList();
    }

    private Sort buildProductSort(String sortBy,String sortOrder){
        return sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
    }

    private Specification<Product> buildClassicSearchSpec(List<String> terms){
        if(terms.isEmpty()){
            return (root,query,cb)-> cb.disjunction();
        }
        return (root,query,cb)->{
            List<Predicate> termPredicates = new ArrayList<>();

            for (String term:terms){
                String likeTerm = "%" + term.toLowerCase() + "%";
                termPredicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("productName")),likeTerm),
                                cb.like(cb.lower(root.get("description")),likeTerm),
                                cb.like(cb.lower(root.get("tags")),likeTerm),
                                cb.like(cb.lower(root.get("category").get("categoryName")),likeTerm)
                        ));
            }
            return cb.or(termPredicates.toArray(Predicate[]::new));
        };
    }
    private ProductDTO mapProductToDTO(Product product){
        ProductDTO productDTO = modelMapper.map(product, ProductDTO.class);
        productDTO.setImage(constructImageUrl(product.getImage()));
        productDTO.setTags(product.getTags());
        return productDTO;
    }

    private ProductResponse buildProductResponse(Page<Product> pageProducts){
        List<ProductDTO> productDTOS = pageProducts.getContent().stream()
                .map(this::mapProductToDTO)
                .toList();
        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(pageProducts.getNumber());
        productResponse.setPageSize(pageProducts.getSize());
        productResponse.setTotalElements(pageProducts.getTotalElements());
        productResponse.setTotalPages(pageProducts.getTotalPages());
        productResponse.setLastPage(pageProducts.isLast());
        return productResponse;
    }

    private ProductResponse buildProductResponse(List<Product> products,Integer pageNumber,Integer pageSize){
        int safePageNumber = pageNumber == null ?  0 : Math.max(pageNumber,0);
        int safePageSize = pageNumber == null ?  10 : Math.max(pageSize,1);
        int fromIndex = Math.min(safePageNumber * safePageSize,products.size());
        int toIndex = Math.min(fromIndex+safePageSize,products.size());
        List<ProductDTO> productDTOS = products.subList(fromIndex,toIndex).stream()
                .map(this::mapProductToDTO)
                .toList();
        ProductResponse productResponse = new ProductResponse();
        productResponse.setContent(productDTOS);
        productResponse.setPageNumber(safePageNumber);
        productResponse.setPageSize(safePageSize);
        productResponse.setTotalElements((long) products.size());
        productResponse.setTotalPages((int) Math.ceil((double) products.size() / safePageSize));
        productResponse.setLastPage(toIndex>=products.size());
        return productResponse;
    }

    private List<Product> mergePrioritizedProducts(List<Product> primaryProducts,List<Product> secondaryProducts){
        Map<Long,Product> mergedProducts =  new LinkedHashMap<>();
        Stream.concat(primaryProducts.stream(),secondaryProducts.stream())
                .forEach(product -> mergedProducts.putIfAbsent(product.getProductId(),product));
        return new ArrayList<>(mergedProducts.values());
    }

    private List<Product> findProductsByOrderedIds(List<Long> productsIds){
        if(productsIds.isEmpty()){
            return List.of();
        }
        Map<Long,Product> productsById = productRepository.findAllById(productsIds).stream()
                .collect(Collectors.toMap(Product::getProductId,product -> product));
        return productsIds.stream()
                .map(productsById::get)
                .filter(product -> product!=null)
                .toList();
    }


}
