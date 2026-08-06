package com.ecommerce.project.service.impl;

import com.ecommerce.project.exception.APIException;
import com.ecommerce.project.exception.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.ProductImage;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import com.ecommerce.project.repository.CartRepository;
import com.ecommerce.project.repository.CategoryRepository;
import com.ecommerce.project.repository.ProductImageRepository;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
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
        // 1. Găsim categoria sau aruncăm excepție
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "categoryId", categoryId));

        // 2. Verificare optimizată direct în baza de date (fără buclă for/încărcare în memorie)
        boolean productExists = productRepository.existsByCategoryAndProductName(category, productDTO.getProductName());

        if (productExists) {
            throw new APIException("Product already exists !!!");
        }

        // 3. Mapare și salvare produs nou
        Product product = modelMapper.map(productDTO, Product.class);
        product.setImage(productDTO.getImage() != null && !productDTO.getImage().isBlank() ? productDTO.getImage() : "cal.png");
        product.setTags(productDTO.getTags());
        product.setCategory(category);
        product.setUser(authUtil.loggedInUser());
        product.setSpecialPrice(calculateSpecialPrice(product.getPrice(), product.getDiscount()));

        Product savedProduct = productRepository.save(product);

        // 4. Indexare semantică și returnare DTO
        productSemanticSearchService.indexProduct(savedProduct);
        return modelMapper.map(savedProduct, ProductDTO.class);
    }


    private String constructImageUrl(String imageName) {
        if (imageName != null && (imageName.startsWith("http://") || imageName.startsWith("https://"))) {
            return imageName;
        }
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
                .map(this::mapProductToDTO)
                .toList();
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
        boolean hasCommaSeparatedTerms = query !=null && query.contains(",");
        List<String> terms = parseSearchTerms(query);
        List<String> classicTerms = hasCommaSeparatedTerms ? terms : buildClassicFallbackTerms(query);
        Sort sortByAndOrder = buildProductSort(sortBy,sortOrder);
        List<Product> classicProducts = productRepository.findAll(buildClassicSearchSpec(classicTerms),sortByAndOrder);
        boolean shouldUseSemanticSearch = Boolean.TRUE.equals(semantic)
                && productSemanticSearchService.isEnabled()
                && !terms.isEmpty();


        if(!shouldUseSemanticSearch){
            return buildProductResponse(classicProducts,pageNumber,pageSize);
        }

        int safePageNumber = pageNumber == null ? 0 : Math.max(pageNumber,0);
        int safePageSize   = pageSize == null ? 10: Math.max(pageSize,1);
        int semanticLimit = Math.max(semanticTopK,(safePageNumber +1) *safePageSize);
        List<Long> semanticProductIds = searchSemanticProductIds(query,terms,hasCommaSeparatedTerms,semanticLimit);
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
    @Caching(evict = {
            @CacheEvict(value = "publicProducts", allEntries = true),
            @CacheEvict(value = "categoryProducts", allEntries = true),
            @CacheEvict(value = "productSearch", allEntries = true),
    })
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
        productFromDB.setSpecialPrice(calculateSpecialPrice(product.getPrice(),product.getDiscount()));
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
        productSemanticSearchService.indexProduct(savedProduct);
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
        productSemanticSearchService.deleteProduct(productId);
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
        productSemanticSearchService.indexProduct(updatedProduct);
        // return DTO after mapping product to DTO
        return modelMapper.map(updatedProduct, ProductDTO.class);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public ProductDTO uploadProductGalleryImages(Long productId, MultipartFile[] images) throws IOException {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (product.getProductImages() == null) {
            product.setProductImages(new ArrayList<>());
        }

        for (MultipartFile file : images) {
            if (file != null && !file.isEmpty()) {
                String fileName = fileService.uploadImage(path, file);
                ProductImage productImage = new ProductImage();
                productImage.setImageName(fileName);
                productImage.setProduct(product);
                product.getProductImages().add(productImage);
            }
        }

        Product savedProduct = productRepository.save(product);
        return mapProductToDTO(savedProduct);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "publicProducts",allEntries = true),
            @CacheEvict(value = "categoryProducts",allEntries = true),
            @CacheEvict(value = "productSearch",allEntries = true)
    })
    public ProductDTO deleteProductGalleryImage(Long productId, Long imageId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        ProductImage imageToRemove = product.getProductImages().stream()
                .filter(img -> img.getImageId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", "imageId", imageId));

        product.getProductImages().remove(imageToRemove);
        productImageRepository.delete(imageToRemove);

        Product savedProduct = productRepository.save(product);
        return mapProductToDTO(savedProduct);
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
        List<String> imageUrls = product.getProductImages() != null
                ? product.getProductImages().stream()
                    .map(img -> constructImageUrl(img.getImageName()))
                    .toList()
                : List.of();
        productDTO.setImages(imageUrls);
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
        int safePageSize = pageSize == null ?  10 : Math.max(pageSize,1);
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
    public List<String> buildClassicFallbackTerms(String query){
        if(query==null || query.isBlank()){
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        String normalizedQuery= query.trim();
        terms.add(normalizedQuery);

        Arrays.stream(normalizedQuery.split("\\s+"))
                .map(this::normalizeSearchToken)
                .filter(token-> token.length() >=3)
                .filter(token-> !isSearchStopWord(token))
                .forEach(terms::add);

        return terms.stream().distinct().toList();

    }

    private String normalizeSearchToken(String token){
        return token ==null ? "":token
                .toLowerCase()
                .replaceAll("^[^\\p{L}\\p{N}] +|[^\\p{L}\\p{N}]+$","");
    }

    private boolean isSearchStopWord(String token){
        return List.of(
                "ce","ca","cu","de","din","la","si","sau","in",
                "pe","un","o","sa","sunt","este","pentru","cand",
                "cat","cum","care","the","and","for","with"
        ).contains(token);
    }

    private List<Long> searchSemanticProductIds(String query,List<String> terms,boolean hasCommaSeparatedTerms,int limit){
        if(hasCommaSeparatedTerms){
            return terms.stream()
                    .flatMap(term -> productSemanticSearchService.searchProductIds(term,limit).stream())
                    .distinct()
                    .limit(limit)
                    .toList();
        }
        return productSemanticSearchService.searchProductIds(query,limit);
    }

    private double calculateSpecialPrice(double price,double discount){
        return  price - ((discount *0.01)*price);
    }

    @Override
    public java.util.List<String> searchAutocomplete(String query) {
        List<Product> products = productRepository.findByProductNameLikeIgnoreCase("%" + query + "%",
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        return products.stream()
                .map(Product::getProductName)
                .distinct()
                .toList();
    }

}
